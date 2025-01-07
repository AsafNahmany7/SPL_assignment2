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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;


/**
 * CameraService is responsible for processing data from the camera and
 * sending DetectObjectsEvents to LiDAR workers.
 * This service interacts with the Camera object to detect objects and updates
 * the system's StatisticalFolder upon sending its observations.
 */
public class CameraService extends MicroService {
    private final Camera camera;
    private final String outputFilePath = "output.json";

    /**
     * Constructor for CameraService.
     *
     * @param camera The Camera object that this service will use to detect objects.
     */
    public CameraService(Camera camera) {
        super("CameraService" + camera.getId());
        this.camera = camera;
    }

    /**
     * Initializes the CameraService.
     * Registers the service to handle TickBroadcasts and sets up callbacks for sending
     * DetectObjectsEvents.
     */
    @Override
    protected void initialize() {

        // הרשמה לטיק
        subscribeBroadcast(TickBroadcast.class, tick -> {
            if (camera.getStatus() == Camera.status.UP) {
                if (camera.getFrequency() < tick.getCurrentTick()) {
                    StampedDetectedObjects stampdetectedObjects = camera.detectObjectsAtTime(tick.getCurrentTick() - camera.getFrequency());
                    if (stampdetectedObjects != null && !stampdetectedObjects.getDetectedObjects().isEmpty()) {
                        boolean errorDetected = stampdetectedObjects.getDetectedObjects()
                                .stream()
                                .anyMatch(obj -> "ERROR".equals(obj.getId()));

                        if (errorDetected) {
                            handleSensorError(stampdetectedObjects); // Handle the error
                        } else {

                        StatisticalFolder statFolder = StatisticalFolder.getInstance();
                        int numbersOfObjects = stampdetectedObjects.getDetectedObjects().size();
                        statFolder.setNumDetectedObjects(numbersOfObjects);

                        sendEvent(new DetectObjectsEvent(stampdetectedObjects, camera.getFrequency()));
                        }
                    }
                }
            }
        });

        // הרשמה ל-TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            camera.setStatus(Camera.status.DOWN);
            terminate();
        });

        // הרשמה ל-CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());
            updateLastCamerasFrame(); // עדכון קובץ output.json
            camera.setStatus(Camera.status.DOWN); //****לא ברור מה משניהם צריך??****
            terminate();//****לא ברור מה משניהם צריך??****
        });
    }

    /**
     * Handles a sensor error, updates the output.json file, and broadcasts a termination signal.
     *
     * @param detectedObjects The detected objects that include the error.
     */
    private void handleSensorError(StampedDetectedObjects detectedObjects) {
        System.err.println("Error detected in camera: " + camera.getId() + ". Terminating all services.");

        // Update the output file with error details
        updateErrorLog(detectedObjects);

        // Broadcast CrashedBroadcast to stop all services
        sendBroadcast(new CrashedBroadcast(getName()));
    }

    /**
     * Updates the output.json file with error details and the last frame of detected objects.
     *
     * @param detectedObjects The detected objects that include the error.
     */
    private void updateErrorLog(StampedDetectedObjects detectedObjects) {
        try (FileReader reader = new FileReader(outputFilePath)) {
            // Load the existing JSON file
            Gson gson = new Gson();
            JsonObject output = JsonParser.parseReader(reader).getAsJsonObject();

            // Find the error description from the detected objects
            String errorDescription = detectedObjects.getDetectedObjects().stream()
                    .filter(obj -> "ERROR".equals(obj.getId()))
                    .findFirst()
                    .map(obj -> obj.getDescription())
                    .orElse("Unknown error");

            // Update the output file with error details
            output.addProperty("error", errorDescription);
            output.addProperty("faultySensor", "Camera" + camera.getId());

            // Update the last frame of the camera
            JsonObject lastCamerasFrame = new JsonObject();
            JsonObject cameraData = new JsonObject();
            cameraData.addProperty("time", detectedObjects.getTime());
            cameraData.add("detectedObjects", gson.toJsonTree(detectedObjects.getDetectedObjects()));
            lastCamerasFrame.add("Camera" + camera.getId(), cameraData);
            output.add("lastCamerasFrame", lastCamerasFrame);

            // Write the updated JSON back to the file
            try (FileWriter writer = new FileWriter(outputFilePath)) {
                gson.toJson(output, writer);
            }
            System.out.println("Error log updated in " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Failed to update error log: " + e.getMessage());
        }
    }

    /**
     * Updates the last frame of the camera in the output.json file.
     */
    private void updateLastCamerasFrame() {
        String outputFilePath = "output.json";

        // בדיקה אם רשימת המסגרות ריקה
        if (camera.getDetectedObjects().isEmpty()) {
            System.err.println("No frames available for Camera" + camera.getId() + " to update.");
            return;
        }

        try (FileReader reader = new FileReader(outputFilePath)) {
            // Load the existing JSON file
            Gson gson = new Gson();
            JsonObject output = JsonParser.parseReader(reader).getAsJsonObject();

            // Create the last frame for the camera
            JsonObject lastCamerasFrame = output.has("lastCamerasFrame")
                    ? output.getAsJsonObject("lastCamerasFrame")
                    : new JsonObject();

            JsonObject cameraData = new JsonObject();
            StampedDetectedObjects lastFrame = camera.getDetectedObjects().get(camera.getDetectedObjects().size() - 1); // המסגרת האחרונה
            cameraData.addProperty("time", lastFrame.getTime());
            cameraData.add("detectedObjects", gson.toJsonTree(lastFrame.getDetectedObjects()));
            lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

            // Update the output file
            output.add("lastCamerasFrame", lastCamerasFrame);

            // Write the updated JSON back to the file
            try (FileWriter writer = new FileWriter(outputFilePath)) {
                gson.toJson(output, writer);
            }
            System.out.println("Last camera frame updated in " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Failed to update last camera frame: " + e.getMessage());
        }
    }
}
