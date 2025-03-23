package bgu.spl.mics.application.services;

import bgu.spl.mics.Callback;
import bgu.spl.mics.Future;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.DetectObjectsEvent;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.*;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * CameraService is responsible for processing data from the camera and
 * sending DetectObjectsEvents to LiDAR workers.
 * This service interacts with the Camera object to detect objects and updates
 * the system's StatisticalFolder upon sending its observations.
 */
public class CameraService extends MicroService {
    private final Camera camera;
    private final String outputFilePath = "output.json";
    private final CountDownLatch latch;
    private StampedDetectedObjects LastFrame;
    /**
     * Constructor for CameraService.
     *
     * @param camera The Camera object that this service will use to detect objects.
     */
    public CameraService(Camera camera, CountDownLatch latch) {
        super("CameraService" + camera.getId());
        this.camera = camera;
        this.latch = latch;
        LastFrame = null;

    }

    /**
     * Initializes the CameraService.
     * Registers the service to handle TickBroadcasts and sets up callbacks for sending
     * DetectObjectsEvents.
     */
    @Override
    protected void initialize() {
        System.out.println("cameraser initialize");

        // הרשמה לטיק
        subscribeBroadcast(TickBroadcast.class, tick -> {


            if (camera.isEmpty()) {
                System.err.println("No frames available for Camera" + camera.getId() + " to update.");
                updateLastCamerasFrame();
                camera.setStatus(Camera.status.DOWN);
                terminate();
                sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class));
            }


            else {
                if (isSystemErrorFlagRaised()) {
                    System.out.println(getName() + " skipping tick processing due to system error");
                    return; // Skip processing this tick
                }


                if (camera.getStatus() == Camera.status.UP) {
                    StampedDetectedObjects statisticObjects = camera.detectObjectsAtTime(tick.getCurrentTick());
                    if (statisticObjects != null) {
                        if (statisticObjects.getDetectedObjects() != null && !statisticObjects.getDetectedObjects().isEmpty()) {
                            StatisticalFolder statFolder = StatisticalFolder.getInstance();

                            // Count only non-ERROR objects
                            int validObjectCount = 0;
                            for (DetectedObject obj : statisticObjects.getDetectedObjects()) {
                                if (!obj.getId().equals("ERROR")) {
                                    System.out.println("GOOD OBJECT \uD83D\uDCAF \uD83D\uDCAF  "+obj.getId() + " at time:"+tick.getCurrentTick());
                                    validObjectCount++;
                                }
                            }

                            statFolder.setNumDetectedObjects(validObjectCount);
                        }
                    }

                    int currentTick = tick.getCurrentTick();
                    this.time=currentTick;

                    if (camera.getFrequency() < currentTick) {

                        StampedDetectedObjects stampdetectedObjects = camera.detectObjectsAtTime(currentTick - camera.getFrequency());

                        if (stampdetectedObjects != null) {

                            if (stampdetectedObjects.getDetectedObjects() != null && !stampdetectedObjects.getDetectedObjects().isEmpty()) {
                                LastFrame = stampdetectedObjects;
                                // Get all non-ERROR objects
                                List<DetectedObject> validObjects = new ArrayList<>();
                                Boolean errorDetected = false;

                            // Separate valid objects from ERROR objects
                                for (DetectedObject currentObj : stampdetectedObjects.getDetectedObjects()) {
                                    if (currentObj.getId().equals("ERROR")) {
                                        System.err.println("הופהה התגלה error במצלמה: " + camera.getId());
                                        errorDetected = true;
                                    } else {
                                        validObjects.add(currentObj);
                                    }
                                }

                     // Process valid objects first if there are any
                                if (!validObjects.isEmpty()) {
                                    // Create a new StampedDetectedObjects with only valid objects
                                    StampedDetectedObjects validStampedObjects = new StampedDetectedObjects(stampdetectedObjects.getTime());
                                    for (DetectedObject obj : validObjects) {
                                        validStampedObjects.addDetectedObject(obj);
                                    }

                                    // Send event for valid objects
                                    sendEvent(new DetectObjectsEvent(validStampedObjects, camera.getFrequency()));

                                    // Add future to camera queue

                                }
// Now handle the ERROR if detected
                                if (errorDetected) {
                                    raiseSystemErrorFlag();
                                    System.out.println( " \uD83D\uDCAF "+ "ERROR GETS PROCESSED at time: "+ tick.getCurrentTick() );
                                    handleSensorError(stampdetectedObjects); // Use the original object that contains the ERROR

                                }
// Remove the processed objects
                                camera.removeStampedObject(stampdetectedObjects);
                            }
                            }
                        }
                    }
                }

        });

        // CrashedBroadcast handler
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());

            // Update last camera frame for output
            updateLastCamerasFrame();

            // Set camera status and notify CamerasManager
            camera.setStatus(Camera.status.DOWN);

            // Terminate
            terminate();
            sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class));
        });

// TerminatedBroadcast handler
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(getName() + " received terminated broadcast.");
            if((terminated.getServiceClass()!=null) && terminated.getServiceClass().equals(TimeService.class)){
                System.out.println(getName()+" recived time termination");
                camera.setStatus(Camera.status.DOWN);
                updateLastCamerasFrame();

                terminate();
                sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class));
            }
        });
        latch.countDown();
        System.out.println("cameraser End initialized ]]]]]]]]]]");
    }

/**
 * Handles a sensor error, updates the FusionSlam output, and broadcasts a termination signal.
 *
 * @param detectedObjects The detected objects that include the error.
 */
private void handleSensorError(StampedDetectedObjects detectedObjects) {
    System.err.println("Error detected in camera: " + camera.getId());
    updateLastCamerasFrame();

    // Update FusionSlam with error details
    FusionSlam fusionSlam = FusionSlam.getInstance();

    // Find the error description from the detected objects
    String errorDescription = detectedObjects.getDetectedObjects().stream()
            .filter(obj -> "ERROR".equals(obj.getId()))
            .findFirst()
            .map(DetectedObject::getDescription)
            .orElse("Unknown error");

    JsonObject errorDetails = new JsonObject();
    errorDetails.addProperty("error", errorDescription);
    errorDetails.addProperty("faultySensor", "Camera" + camera.getId());
    errorDetails.addProperty("errorTime", detectedObjects.getTime());  // Add the error time
    System.out.println("Camera error detected at time: " + detectedObjects.getTime());

    JsonObject lastCamerasFrame = new JsonObject();
    JsonObject cameraData = new JsonObject();
    cameraData.addProperty("time", detectedObjects.getTime());
    cameraData.add("detectedObjects", new Gson().toJsonTree(detectedObjects.getDetectedObjects()));
    lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

    errorDetails.add("lastCamerasFrame", lastCamerasFrame);

    fusionSlam.updateOutput("errorDetails", errorDetails);

    // Mark the camera as having an ERROR status
    camera.setStatus(Camera.status.ERROR);

    // Terminate this service
    terminate();
    sendBroadcast(new CrashedBroadcast(camera.getKey()));
}

/**
 * Updates the FusionSlam output with error details and the last frame of detected objects.
 *
 * @param detectedObjects The detected objects that include the error.
 */
        private void updateErrorLog(StampedDetectedObjects detectedObjects) {
            FusionSlam fusionSlam = FusionSlam.getInstance();

            // Find the error description from the detected objects
            String errorDescription = detectedObjects.getDetectedObjects().stream()
                    .filter(obj -> "ERROR".equals(obj.getId()))
                    .findFirst()
                    .map(DetectedObject::getDescription)
                    .orElse("Unknown error");

            JsonObject errorDetails = new JsonObject();
            errorDetails.addProperty("error", errorDescription);
            errorDetails.addProperty("faultySensor", "Camera" + camera.getId());

            JsonObject lastCamerasFrame = new JsonObject();
            JsonObject cameraData = new JsonObject();
            cameraData.addProperty("time", detectedObjects.getTime());
            cameraData.add("detectedObjects", new Gson().toJsonTree(detectedObjects.getDetectedObjects()));
            lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

            errorDetails.add("lastCamerasFrame", lastCamerasFrame);

            fusionSlam.updateOutput("errorDetails", errorDetails);
        }

    public Camera getCamera() {

            return camera;
    }

    /**
 * Updates the last frame of the camera in the FusionSlam output.
 */
        private void updateLastCamerasFrame() {
            if (LastFrame == null) {
                System.err.println("No frames detected at all at Camera: " + camera.getId());
                return;
           }

            FusionSlam fusionSlam = FusionSlam.getInstance();
            JsonObject lastCamerasFrame = new JsonObject();
            JsonObject cameraData = new JsonObject();

            //StampedDetectedObjects lastFrame = camera.getDetectedObjects().get(camera.getDetectedObjects().size() - 1);
            cameraData.addProperty("time", LastFrame.getTime());
            cameraData.add("detectedObjects", new Gson().toJsonTree(LastFrame.getDetectedObjects()));
            lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

            fusionSlam.updateOutput("lastCamerasFrame", lastCamerasFrame);
        }
 }