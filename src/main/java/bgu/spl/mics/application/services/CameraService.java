package bgu.spl.mics.application.services;

import bgu.spl.mics.Callback;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.DetectObjectsEvent;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.Camera;
import bgu.spl.mics.application.objects.StampedDetectedObjects;
import bgu.spl.mics.application.objects.StatisticalFolder;
import bgu.spl.mics.application.objects.FusionSlam;
import bgu.spl.mics.application.objects.DetectedObject;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

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

    /**
     * Constructor for CameraService.
     *
     * @param camera The Camera object that this service will use to detect objects.
     */
    public CameraService(Camera camera, CountDownLatch latch) {
        super("CameraService" + camera.getId());
        this.camera = camera;
        this.latch = latch;
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
            if (camera.isEmpty()) { //נחמני מחק את זה אצלו,נמצא במקום אחר ?
                System.out.println("בדיקה - האם cameraservice שולח terminatebroadcast");
                sendBroadcast(new TerminatedBroadcast(this.getName()));
                camera.setStatus(Camera.status.DOWN);
                terminate();
            }else {
                if (camera.getStatus() == Camera.status.UP) {
                    System.out.println("בדיקה - תנאי מצלמה up");
                    StampedDetectedObjects statisticObjects = camera.detectObjectsAtTime(tick.getCurrentTick());
                    System.out.println(tick.getCurrentTick() + "בדיקה במצלמה-סר - מס' tick:");
                    if (statisticObjects != null) {
                        System.out.println("בזמן טיק" + tick.getCurrentTick() + "stampedobject לא null");
                        if (statisticObjects.getDetectedObjects() != null && !statisticObjects.getDetectedObjects().isEmpty()) {
                            System.out.println("בדיקה במצלמה-סר - detectobject לא null");
                            StatisticalFolder statFolder = StatisticalFolder.getInstance();
                            int numbersOfObjects = statisticObjects.getDetectedObjects().size();
                            statFolder.setNumDetectedObjects(numbersOfObjects);
                        }
                    }
                    int currentTick = tick.getCurrentTick();
                    if (camera.getFrequency() < currentTick) {
                        System.out.println("בדיקה במצלמה-סר - תדירות קטן מהטיק");
                        StampedDetectedObjects stampdetectedObjects = camera.detectObjectsAtTime(currentTick - camera.getFrequency());
                        if (stampdetectedObjects != null) {
                            if (stampdetectedObjects.getDetectedObjects() != null && !stampdetectedObjects.getDetectedObjects().isEmpty()) {
                                System.out.println("בדיקה - שיש אובייקט בתנאי התדירות מול טיק");
                                Boolean errorDetected = false;
                                for (DetectedObject currenObj : stampdetectedObjects.getDetectedObjects()){
                                    System.out.println("עובר על האובייקטים לחפש error");
                                    System.out.println("currentObj.getId() ==" + currenObj.getId());
                                    if(currenObj.getId().equals("ERROR")){
                                        System.out.println("מצא error");
                                        errorDetected = true;
                                        break;
                                    }
                                }

                                if (errorDetected) {
                                    System.out.println("בדיקה - האם יש error");
                                    handleSensorError(stampdetectedObjects); // Handle the error
                                } else {
                                    System.out.println(currentTick + "מצלמה-סר שולח event של טיק: ");
                                    sendEvent(new DetectObjectsEvent(stampdetectedObjects, camera.getFrequency()));
                                    camera.removeStampedObject(stampdetectedObjects);
                                }
                            }
                        }
                    }
                }
            }
        });

        // הרשמה ל-CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());
            updateLastCamerasFrame(); // עדכון המידע ב-FusionSlam
            camera.setStatus(Camera.status.DOWN);
            terminate();
        });
        // הרשמה ל-TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {  ////
            System.out.println(getName() + " received terminated broadcast.");
            terminate();
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

            // Update FusionSlam with error details
            updateErrorLog(detectedObjects);

            System.err.println("Camera: " + camera.getId() + " sending CrashedBroadcast");
            // Broadcast CrashedBroadcast to stop all services
            sendBroadcast(new CrashedBroadcast(getName()));
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

/**
 * Updates the last frame of the camera in the FusionSlam output.
 */
        private void updateLastCamerasFrame() {
            if (camera.getDetectedObjects().isEmpty()) {
                System.err.println("No frames available for Camera" + camera.getId() + " to update.");
                return;
            }

            FusionSlam fusionSlam = FusionSlam.getInstance();
            JsonObject lastCamerasFrame = new JsonObject();
            JsonObject cameraData = new JsonObject();

            StampedDetectedObjects lastFrame = camera.getDetectedObjects().get(camera.getDetectedObjects().size() - 1);
            cameraData.addProperty("time", lastFrame.getTime());
            cameraData.add("detectedObjects", new Gson().toJsonTree(lastFrame.getDetectedObjects()));
            lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

            fusionSlam.updateOutput("lastCamerasFrame", lastCamerasFrame);
        }
 }