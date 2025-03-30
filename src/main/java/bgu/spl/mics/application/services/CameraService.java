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
import java.util.concurrent.atomic.AtomicInteger;

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
    private int lastProcessedTick;
    private List<StampedDetectedObjects> gotDetected;
    private StatisticalFolder stats;
    private boolean errorFound = false;
    private StampedDetectedObjects lastSentFrame = null;
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
        lastProcessedTick = 0;
        gotDetected = new ArrayList<>();
        stats = StatisticalFolder.getInstance();
        errorFound=false;
    }

    /**
     * Initializes the CameraService.
     * Registers the service to handle TickBroadcasts and sets up callbacks for sending
     * DetectObjectsEvents.
     */
    @Override
    protected void initialize() {
        System.out.println("cameraser initialize");
        stats.registerCameraService(this);

        // הרשמה לטיק
        subscribeBroadcast(TickBroadcast.class, tick -> {
            AtomicInteger detections = new AtomicInteger(0);

            if (camera.isEmpty()) {
                System.err.println("No frames available for Camera" + camera.getId() + " to update.");
                updateLastCamerasFrame();
                camera.setStatus(Camera.status.DOWN);
                terminate();
                sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class,this));
                return;
            }

            time = tick.getCurrentTick();
            updateStats();
            StampedDetectedObjects currentDetection = camera.detectObjectsAtTime(time);
            if(currentDetection != null) {
                for (DetectedObject currentDO : currentDetection.getDetectedObjects()) {
                    if (currentDO.getId().equals("ERROR")) {
                        errorFound = true;
                        handleSensorError(currentDetection);
                        return;
                    }
                }
                //LastFrame = currentDetection;
            }

            StampedDetectedObjects toSend = camera.detectObjectsAtTime(time-camera.getFrequency());
            if(toSend!=null){
                LastFrame = toSend;
                lastSentFrame = toSend;
                camera.getDetectedObjects().remove(toSend);
                sendEvent(new DetectObjectsEvent(toSend, time-camera.getFrequency()));

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
            sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class,this));
        });

// TerminatedBroadcast handler
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(getName() + " received terminated broadcast.");
            if((terminated.getServiceClass()!=null) && terminated.getServiceClass().equals(TimeService.class)){
                System.out.println(getName()+" recived time termination");
                camera.setStatus(Camera.status.DOWN);
                updateLastCamerasFrame();

            //    System.out.println(getName() + " received TerminatedBroadcast at " + System.currentTimeMillis());
                this.terminate();
            //    System.out.println(getName() + " finished termination logic at " + System.currentTimeMillis());
                sendBroadcast(new TerminatedBroadcast(this.getName(),CameraService.class,this));
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

    // ננסה להביא את הפריים הקודם ל־ERROR
    //StampedDetectedObjects prev = camera.detectObjectsAtTime(time - camera.getFrequency());
    if (lastSentFrame != null) {
        LastFrame = lastSentFrame;
    } else {
        System.err.println("⚠️ No lastSentFrame available before ERROR at Camera" + camera.getId());
        LastFrame = detectedObjects;  // fallback only if nothing else is available
    }

    updateLastCamerasFrame();

    // Update FusionSlam with error details
    FusionSlam fusionSlam = FusionSlam.getInstance();

    // Find the error description
    String errorDescription = detectedObjects.getDetectedObjects().stream()
            .filter(obj -> "ERROR".equals(obj.getId()))
            .findFirst()
            .map(DetectedObject::getDescription)
            .orElse("Unknown error");

    JsonObject errorDetails = new JsonObject();
    errorDetails.addProperty("error", errorDescription);
    errorDetails.addProperty("faultySensor", "Camera" + camera.getId());
    errorDetails.addProperty("errorTime", detectedObjects.getTime());

    JsonObject lastCamerasFrame = new JsonObject();
    JsonObject cameraData = new JsonObject();
    cameraData.addProperty("time", detectedObjects.getTime());
    cameraData.add("detectedObjects", new Gson().toJsonTree(detectedObjects.getDetectedObjects()));
    lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

    errorDetails.add("lastCamerasFrame", lastCamerasFrame);

    fusionSlam.updateOutput("errorDetails", errorDetails);

    camera.setStatus(Camera.status.ERROR);
    FusionSlam fs = FusionSlam.getInstance();
    fs.crashTime.compareAndSet(-1, time);

    raiseSystemErrorFlag();
    fs.setCrasherServiceClass(CameraService.class);
    terminate();
    sendBroadcast(new CrashedBroadcast(camera.getKey(), time, CameraService.class, this));
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

    private void updateStats(){
        boolean statsError = false;
        StampedDetectedObjects detectedNow = camera.detectObjectsAtTime(time);
        if(detectedNow == null){
            DetectStat DS =new DetectStat(time,0);
            stats.updateCurrentDetectedObjects(this,DS);
            return;
        }

        for(DetectedObject current : detectedNow.getDetectedObjects()) {
            if(current.getId().equals("ERROR")){
                return;
            }
        }

        DetectStat DS =new DetectStat(time,detectedNow.getDetectedObjects().size());
        stats.updateCurrentDetectedObjects(this,DS);
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

        // שליפה של פלט קיים כדי לא לאפס מצלמות קודמות
        JsonObject output = fusionSlam.getOutputData();
        JsonObject lastCamerasFrame = output.has("lastCamerasFrame")
                ? output.getAsJsonObject("lastCamerasFrame")
                : new JsonObject();

        JsonObject cameraData = new JsonObject();
        cameraData.addProperty("time", LastFrame.getTime());
        cameraData.add("detectedObjects", new Gson().toJsonTree(LastFrame.getDetectedObjects()));

        lastCamerasFrame.add("Camera" + camera.getId(), cameraData);

        // עדכון שמכיל את כל המצלמות עד כה
        fusionSlam.updateOutput("lastCamerasFrame", lastCamerasFrame);

        // לוג לבדיקה
        System.out.println("🔍 Added Camera" + camera.getId() + " to lastCamerasFrame");
    }





}