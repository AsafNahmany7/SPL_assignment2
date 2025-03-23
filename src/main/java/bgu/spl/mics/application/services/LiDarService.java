package bgu.spl.mics.application.services;

import bgu.spl.mics.Broadcast;
import bgu.spl.mics.Future;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * LiDarService is responsible for processing data from the LiDAR sensor and
 * sending TrackedObjectsEvents to the FusionSLAM service.
 */
public class LiDarService extends MicroService {

    int cameraTerminations;
    int numOfCameras;
    private LiDarWorkerTracker tracker;
    private List<StampedDetectedObjects> DetectedObjectsbyTime;
    private final CountDownLatch latch;
    private int lastTime;
    private StampedDetectedObjects LastFrame;


    public LiDarService(LiDarWorkerTracker tracker, CountDownLatch latch,int numOfCameras) {
        super("LidarWorker " + tracker.getId());
        this.tracker = tracker;
        DetectedObjectsbyTime = new ArrayList<>();
        this.latch = latch;
        this.numOfCameras = numOfCameras;
        this.cameraTerminations = 0;
    }

    @Override
    protected void initialize() {
        System.out.println("lidarser initialize");

        //tick callback:
        subscribeBroadcast(TickBroadcast.class, (TickBroadcast tick) -> {

            if(isSystemErrorFlagRaised())
                return;

            int time = tick.getCurrentTick();
            this.time = time;
            lastTime = time;
            LiDarDataBase database = LiDarDataBase.getInstance();
            StampedDetectedObjects toProcess = null;
            int detectionTime = 0;

            // Check if the current time matches an ERROR in the database
            for (StampedCloudPoints cloudPoints : database.getStampedCloud()) {
                if (cloudPoints.getId().equals("ERROR") && cloudPoints.getTime() == time) {
                    System.out.println("Detected ERROR object in LiDAR database at time " + time);
                    lastTime = time; // Set the time for error reporting
                    handleSensorError("LiDAR Malfunction detected in database");
                    return; // Stop processing if error found
                }
            }

            for (StampedDetectedObjects currentTimeDetectedObjects : DetectedObjectsbyTime) {
                detectionTime = currentTimeDetectedObjects.getTime();

                if (detectionTime + tracker.getFrequencey() <= time) {
                    toProcess = currentTimeDetectedObjects;
                } else {
                }
            }

            if (toProcess != null) {
                List<TrackedObject> trackedObjects = new ArrayList<>();
                boolean errorDetected = false;
                String errorDescription = "";

                // First pass: Process all non-ERROR objects
                for (DetectedObject currentDetectedObject : toProcess.getDetectedObjects()) {
                    if(currentDetectedObject == null){
                        break;
                    }
                    String id = currentDetectedObject.getId();

                    if (id.equals("ERROR")) {
                        errorDetected = true;
                        errorDescription = currentDetectedObject.getDescription();
                        tracker.setStatus(LiDarWorkerTracker.status.ERROR);
                        continue; // Skip this object but continue processing others
                    }

                    StampedCloudPoints correspondingCloudPoints = database.searchStampedClouds(detectionTime, id);

                    // Add null check here
                    if (correspondingCloudPoints == null) {
                        System.err.println("No cloud points found for object: " + id + " at time: " + detectionTime);
                        continue;
                    }

                    List<CloudPoint> cloudpoints = new ArrayList<>();

                    for (List<Double> coordinates : correspondingCloudPoints.getCloudPoints()) {
                        cloudpoints.add(new CloudPoint(coordinates.get(0), coordinates.get(1)));
                    }

                    System.out.println("-----ניצור TrackedObject-----");
                    TrackedObject newTrackedObject = new TrackedObject(id, toProcess.getTime(), currentDetectedObject.getDescription(), cloudpoints);
                    trackedObjects.add(newTrackedObject);
                    tracker.addTrackedObject(newTrackedObject); // Save to last tracked objects for error reporting
                }

                // Process valid tracked objects
                if (!trackedObjects.isEmpty()) {
                    StatisticalFolder.getInstance().setNumTrackedObjects(trackedObjects.size());

                    TrackedObjectsEvent output = new TrackedObjectsEvent(trackedObjects);
                    sendEvent(output);

                    // Add the future to the LidarsManager
                }

                // Remove the processed object after handling all contents
                DetectedObjectsbyTime.remove(toProcess);

                // Finally, handle any error after processing all valid objects
                if (errorDetected) {
                    raiseSystemErrorFlag();
                    handleSensorError(errorDescription);
                }
            }
        });

        //DetectedObjectEvent callback
        subscribeEvent(DetectObjectsEvent.class, (DetectObjectsEvent objEvent) -> {
            StampedDetectedObjects stampedDetectedObjects = objEvent.getDetectedObjects();

            // Check for ERROR objects before adding to the processing queue
            boolean hasError = false;
            String errorDescription = "";

            for (DetectedObject dodo : stampedDetectedObjects.getDetectedObjects()) {
                System.out.println("popa popa lidar" + tracker.getId() + " recieved " + dodo.getId() + " at time : " + stampedDetectedObjects.getTime());

                if (dodo.getId().equals("ERROR")) {
                    hasError = true;
                    errorDescription = dodo.getDescription();
                    // Set the time for error reporting
                    lastTime = stampedDetectedObjects.getTime();
                }
            }

            // If we found an ERROR object, handle it immediately
            if (hasError) {
                handleSensorError(errorDescription);
                return; // Don't add this to the queue
            }

            // Add to processing queue if no errors
            DetectedObjectsbyTime.add(stampedDetectedObjects);
        });

        // TerminatedBroadcast handler
        subscribeBroadcast(TerminatedBroadcast.class, (TerminatedBroadcast broadcast) -> {
            if((broadcast.getServiceClass()!=null) && broadcast.getServiceClass().equals(TimeService.class)){
                System.out.println(getName()+" recived time termination broadcast");
                updateLastLiDARFrame();
                terminate();
                sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class));
            }

            else if((broadcast.getServiceClass()!=null) && broadcast.getServiceClass().equals(CameraService.class)){
                System.out.println(getName()+" recived camera termination broadcast");
                cameraTerminations++;
                if(cameraTerminations == numOfCameras){
                    processTheRestObjects();
                    tracker.setStatus(LiDarWorkerTracker.status.DOWN);
                    //updateLastLiDARFrame();
                    terminate();
                    sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class));

                }

            }
        });

        //crash callback
        subscribeBroadcast(CrashedBroadcast.class, (CrashedBroadcast broadcast) -> {
            System.out.println("lidarser received crash notification from: " + broadcast.getServiceName());
            updateLastLiDARFrame();
            tracker.setStatus(LiDarWorkerTracker.status.DOWN);
            terminate();
            sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class));

        });

        latch.countDown();

        System.out.println("lidarser End initialized ]]]]]]]]]]");
    }

    private void processDetectedObjects(StampedDetectedObjects detectedObjects, int currentTime) {
        int detectionTime = detectedObjects.getTime();
        LiDarDataBase database = LiDarDataBase.getInstance();
        List<TrackedObject> trackedObjects = new ArrayList<>();

        for (DetectedObject currentDetectedObject : detectedObjects.getDetectedObjects()) {
            String id = currentDetectedObject.getId();

            if (id.equals("ERROR")) {
                handleSensorError(currentDetectedObject.getDescription());
                return;
            }

            StampedCloudPoints correspondingCloudPoints = database.searchStampedClouds(detectionTime, id);

            List<CloudPoint> cloudpoints = new ArrayList<>();
            for (List<Double> coordinates : correspondingCloudPoints.getCloudPoints()) {
                cloudpoints.add(new CloudPoint(coordinates.get(0), coordinates.get(1)));
            }

            TrackedObject newTrackedObject = new TrackedObject(id, detectionTime, currentDetectedObject.getDescription(), cloudpoints);
            trackedObjects.add(newTrackedObject);
            tracker.addTrackedObject(newTrackedObject);
        }

        if (!trackedObjects.isEmpty()) {
            TrackedObjectsEvent event = new TrackedObjectsEvent(trackedObjects);
            sendEvent(event);



            // Update statistics
            StatisticalFolder.getInstance().setNumTrackedObjects(trackedObjects.size());
        }

        // Remove processed objects
        DetectedObjectsbyTime.remove(detectedObjects);
    }

    private void handleSensorError(String errorDescription) {
        System.err.println("Error detected in LiDAR:\uD83D\uDC36\uD83D\uDC36 " + tracker.getId());

        updateLastLiDARFrame();
        // Update FusionSlam with error details
        FusionSlam fusionSlam = FusionSlam.getInstance();

        JsonObject errorDetails = new JsonObject();
        errorDetails.addProperty("error", errorDescription);
        errorDetails.addProperty("faultySensor", "LiDAR" + tracker.getId());
        errorDetails.addProperty("errorTime", lastTime);  // Add the error time
        System.out.println("LiDAR error detected at time: " + lastTime);

        // Add last tracked objects if available
        if (!tracker.getLastTrackedObjects().isEmpty()) {
            JsonObject lastTrackedObjectsFrame = new JsonObject();
            JsonObject lidarData = new JsonObject();
            lidarData.addProperty("time", lastTime);
            lidarData.add("trackedObjects", new Gson().toJsonTree(tracker.getLastTrackedObjects()));
            lastTrackedObjectsFrame.add("LiDAR" + tracker.getId(), lidarData);
            errorDetails.add("lastLiDARFrame", lastTrackedObjectsFrame);
        }

        fusionSlam.updateOutput("errorDetails", errorDetails);

        // Mark the LiDAR as having an ERROR status
        tracker.setStatus(LiDarWorkerTracker.status.ERROR);

        System.err.println("LiDAR: " + tracker.getId() + " sending CrashedBroadcast");
        // Broadcast CrashedBroadcast to stop all services

        System.out.println("Lidar sending crash");
        sendBroadcast(new CrashedBroadcast(getName()));

        // Terminate this service
        terminate();
    }



   private void updateLastLiDARFrame() {
        if (tracker.getLastTrackedObjects().isEmpty()) {
            System.err.println("No tracked objects available for LiDAR" + tracker.getId() + " to update.");
            return;
        }

        FusionSlam fusionSlam = FusionSlam.getInstance();
        JsonObject lastLiDARFrame = new JsonObject();
        JsonObject lidarData = new JsonObject();

        // Use the lastTime directly (which should be 4 for the error case)
        lidarData.addProperty("time", lastTime);

        // Create a list of tracked objects to include in the frame
        List<TrackedObject> lastsFrames = new ArrayList<>();
        for (TrackedObject obj : tracker.getLastTrackedObjects()) {
            lastsFrames.add(obj);
        }

        lidarData.add("trackedObjects", new Gson().toJsonTree(lastsFrames));
        lastLiDARFrame.add("LiDAR" + tracker.getId(), lidarData);
        fusionSlam.updateOutput("lastLiDARFrame", lastLiDARFrame);
    }

    private void processTheRestObjects (){
        LiDarDataBase database = LiDarDataBase.getInstance();
        int detectionTime = 0;

        for (StampedDetectedObjects currentTimeDetectedObjects : DetectedObjectsbyTime) {
            detectionTime = currentTimeDetectedObjects.getTime();
            List<TrackedObject> trackedObjects = new ArrayList<>();
            boolean errorDetected = false;
            String errorDescription = "";

            // First pass: Process all non-ERROR objects
            for (DetectedObject currentDetectedObject : currentTimeDetectedObjects.getDetectedObjects()) {
                String id = currentDetectedObject.getId();

                if (id.equals("ERROR")) {
                    errorDetected = true;
                    errorDescription = currentDetectedObject.getDescription();
                    continue; // Skip this object but continue processing others
                }

                StampedCloudPoints correspondingCloudPoints = database.searchStampedClouds(detectionTime, id);
                List<CloudPoint> cloudpoints = new ArrayList<>();

                for (List<Double> coordinates : correspondingCloudPoints.getCloudPoints()) {
                    cloudpoints.add(new CloudPoint(coordinates.get(0), coordinates.get(1)));
                }

                System.out.println("-----ניצור TrackedObject-----");
                TrackedObject newTrackedObject = new TrackedObject(id, currentTimeDetectedObjects.getTime(), currentDetectedObject.getDescription(), cloudpoints);
                trackedObjects.add(newTrackedObject);
                tracker.addTrackedObject(newTrackedObject); // Save to last tracked objects for error reporting
            }

            // Process valid tracked objects
            if (!trackedObjects.isEmpty()) {
                StatisticalFolder.getInstance().setNumTrackedObjects(trackedObjects.size());

                TrackedObjectsEvent output = new TrackedObjectsEvent(trackedObjects);
                sendEvent(output);

                // Add the future to the LidarsManager

            }
            // Remove the processed object after handling all contents
            DetectedObjectsbyTime.remove(currentTimeDetectedObjects);

            // Finally, handle any error after processing all valid objects
            if (errorDetected) {
                handleSensorError(errorDescription);
            }
        }
        if (DetectedObjectsbyTime.isEmpty()) {
            System.err.println("No frames available for Lidar" + tracker.getId() + " to update.");
            return;
        }
    }
}