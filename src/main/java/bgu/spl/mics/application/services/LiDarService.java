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
            int time = tick.getCurrentTick();
            this.time =time;
            lastTime = time;
            LiDarDataBase database = LiDarDataBase.getInstance();
            StampedDetectedObjects toProcess = null;
            int detectionTime = 0;

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
                    handleSensorError(errorDescription);
                }
            }
        });

        //DetectedObjectEvent callback
        subscribeEvent(DetectObjectsEvent.class, (DetectObjectsEvent objEvent) -> {
            StampedDetectedObjects stampedDetectedObjects = objEvent.getDetectedObjects();

            for (DetectedObject dodo : stampedDetectedObjects.getDetectedObjects()) {
                System.out.println("popa popa lidar" + tracker.getId() + " recieved " + dodo.getId() + " at time : " + stampedDetectedObjects.getTime());
            }

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
        System.err.println("Error detected in LiDAR: " + tracker.getId());

        updateLastLiDARFrame();
        // Update FusionSlam with error details
        FusionSlam fusionSlam = FusionSlam.getInstance();

        JsonObject errorDetails = new JsonObject();
        errorDetails.addProperty("error", errorDescription);
        errorDetails.addProperty("faultySensor", "LiDAR" + tracker.getId());

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

        // Wait for any pending futures to complete


        // Mark this LiDAR as done in the LidarsManager


        System.err.println("LiDAR: " + tracker.getId() + " sending CrashedBroadcast");
        // Broadcast CrashedBroadcast to stop all services
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

        int LastFrameTime = tracker.getLastTrackedObjects().getLast().getTime();

        lidarData.addProperty("time", LastFrameTime);
        List<TrackedObject> lastsFrames = new ArrayList<>();
        for(TrackedObject last : tracker.getLastTrackedObjects()){
            if(last.getTime() == LastFrameTime){
                lastsFrames.add(tracker.getLastTrackedObjects().removeLast());
            }
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