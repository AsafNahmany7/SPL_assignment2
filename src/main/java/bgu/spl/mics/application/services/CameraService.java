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
            if (camera.getStatus() == Camera.status.UP) {
                StampedDetectedObjects statisticObjects = camera.detectObjectsAtTime(tick.getCurrentTick());
                if (statisticObjects != null && statisticObjects.getDetectedObjects() != null && !statisticObjects.getDetectedObjects().isEmpty()) {
                    StatisticalFolder statFolder = StatisticalFolder.getInstance();
                    int numbersOfObjects = statisticObjects.getDetectedObjects().size();
                    statFolder.setNumDetectedObjects(numbersOfObjects);
                }
                if (camera.getFrequency() < tick.getCurrentTick()) {
                    StampedDetectedObjects stampdetectedObjects = camera.detectObjectsAtTime(tick.getCurrentTick() - camera.getFrequency());
                    if (stampdetectedObjects != null && stampdetectedObjects.getDetectedObjects() != null && !stampdetectedObjects.getDetectedObjects().isEmpty()) {
                        boolean errorDetected = stampdetectedObjects.getDetectedObjects()
                                .stream()
                                .anyMatch(obj -> "ERROR".equals(obj.getId()));

                        if (errorDetected) {
                            handleSensorError(stampdetectedObjects); // Handle the error
                        } else {
                        sendEvent(new DetectObjectsEvent(stampdetectedObjects, camera.getFrequency()));
                        }
                    }
                }
            }
        });

        // הרשמה ל-TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(this.getName() + " receive terminated ----------------");
            camera.setStatus(Camera.status.DOWN);//****לא ברור מה משניהם צריך??****
            terminate();//****לא ברור מה משניהם צריך??****
            System.out.println(this.getName() + " after terminated ----------?????");
        });

        // הרשמה ל-CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());
            updateLastCamerasFrame(); // עדכון המידע ב-FusionSlam
            camera.setStatus(Camera.status.DOWN);
            terminate();
        });
        latch.countDown();
        System.out.println("fusionslamser End initialized ]]]]]]]]]]");
    }

/**
 * Handles a sensor error, updates the FusionSlam output, and broadcasts a termination signal.
 *
 * @param detectedObjects The detected objects that include the error.
 */
        private void handleSensorError(StampedDetectedObjects detectedObjects) {
            System.err.println("Error detected in camera: " + camera.getId() + ". Terminating all services.");

            // Update FusionSlam with error details
            updateErrorLog(detectedObjects);

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