package bgu.spl.mics.application.services;

import bgu.spl.mics.Broadcast;
import bgu.spl.mics.Future;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
    private List<StampedTrackedObjects> trackedObjects;
    private final CountDownLatch latch;
    private int lastTime;
    private boolean CamerasTerminatedFlag = false;
    private StatisticalFolder stats;

    public LiDarService(LiDarWorkerTracker tracker, CountDownLatch latch,int numOfCameras) {
        super("LidarWorker " + tracker.getId());
        this.tracker = tracker;
        this.latch = latch;
        this.numOfCameras = numOfCameras;
        this.cameraTerminations = 0;
        this.trackedObjects = new ArrayList<>();
        this.stats=StatisticalFolder.getInstance();
    }

    @Override
    protected void initialize() {
        System.out.println("lidarser initialize");
        stats.registerLidarService(this);

        //tick callback:
        subscribeBroadcast(TickBroadcast.class, (TickBroadcast tick) -> {

            if(isSystemErrorFlagRaised())
                return;

            time = tick.getCurrentTick();


            //scenario of all the cameras terminated and no more objects to track
            if (CamerasTerminatedFlag && trackedObjects.isEmpty()){
                tracker.setStatus(LiDarWorkerTracker.status.DOWN);
                //updateLastLiDARFrame();
                System.out.println(getName() + " received TerminatedBroadcast at \uD83E\uDDE0\uD83E\uDDE0\uD83E\uDDE0\uD83E\uDDE0\uD83E\uDDE0\uD83E\uDDE0 " + System.currentTimeMillis());
                this.terminate();
                System.out.println(getName() + " finished termination logic at \uD83E\uDDE0\uD83D\uDCDA\uD83E\uDD13\uD83D\uDCDA\uD83E\uDD13" + System.currentTimeMillis());
                sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class,this));
            }

            processAndSendTrackedEvent();

        });

        //DetectedObjectEvent callback
        subscribeEvent(DetectObjectsEvent.class, (DetectObjectsEvent objEvent) -> {
            StampedDetectedObjects stampedDetectedObjects = objEvent.getDetectedObjects();
            LiDarDataBase dataBase = LiDarDataBase.getInstance();
            int detectionTime = stampedDetectedObjects.getTime();
            StampedTrackedObjects STO = new StampedTrackedObjects(detectionTime);

            boolean errorFound = false;
            for(DetectedObject detectedObject: stampedDetectedObjects.getDetectedObjects()){
                StampedCloudPoints currentCP = dataBase.searchStampedClouds(detectionTime,detectedObject.getId());

                if(currentCP.getId().equals("ERROR"))
                    errorFound=true;



                List<CloudPoint> cloudPoints = new ArrayList<>();
                for(List<Double> coordinates : currentCP.getCloudPoints()){
                    cloudPoints.add(new CloudPoint(coordinates.get(0),coordinates.get(1)));
                }
                TrackedObject TO = new TrackedObject(currentCP.getId(), detectionTime, detectedObject.getDescription(),cloudPoints);
                STO.addTrackedObject(TO);
            }
            trackedObjects.add(STO);

            if(!errorFound)
                updateStats(STO);


            //special case freq ==0
            if(tracker.getFrequencey()==0)
                processAndSendTrackedEvent();

        });

        // TerminatedBroadcast handler
        subscribeBroadcast(TerminatedBroadcast.class, (TerminatedBroadcast broadcast) -> {
            if((broadcast.getServiceClass()!=null) && broadcast.getServiceClass().equals(TimeService.class)){
                System.out.println(getName()+" recived time termination broadcast⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽");
                updateLastLiDARFrame();
                terminate();
                sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class,this));
            }

            else if((broadcast.getServiceClass()!=null) && broadcast.getServiceClass().equals(CameraService.class)){
                System.out.println(getName()+" recived camera termination broadcast⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽");
                cameraTerminations++;


                if(cameraTerminations == numOfCameras){
                    CamerasTerminatedFlag = true;

                    if(trackedObjects.isEmpty()){
                        tracker.setStatus(LiDarWorkerTracker.status.DOWN);
                        //updateLastLiDARFrame();
                        System.out.println("⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽ terminate1 ");
                        terminate();
                        sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class,this));
                        System.out.println("⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽ after terminate 1");

                    }
                }
            }
        });

        //crash callback
        subscribeBroadcast(CrashedBroadcast.class, (CrashedBroadcast broadcast) -> {
            System.out.println("lidarser received crash notification from: " + broadcast.getServiceName());
            updateLastLiDARFrame();
            tracker.setStatus(LiDarWorkerTracker.status.DOWN);

            terminate();
            sendBroadcast(new TerminatedBroadcast(getName(), LiDarService.class,this));

        });

        latch.countDown();

        System.out.println("lidarser End initialized ]]]]]]]]]]");
    }



    private void handleSensorError(int detTime) {
        System.err.println("Error detected in LiDAR:\uD83D\uDC36\uD83D\uDC36 " + tracker.getId());

        updateLastLiDARFrame();
        // Update FusionSlam with error details
        FusionSlam fusionSlam = FusionSlam.getInstance();

        JsonObject errorDetails = new JsonObject();
        errorDetails.addProperty("error", "");
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

        FusionSlam fs = FusionSlam.getInstance();
        fs.crashTime.compareAndSet(-1,detTime);
        raiseSystemErrorFlag();
        terminate();
        System.out.println("Lidar sending crash⚽");
        sendBroadcast(new CrashedBroadcast(getName(),detTime,LiDarService.class,this));

        // Terminate this service

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

    private void updateStats(StampedTrackedObjects STO){
        System.out.println("Adding tracks stats: time=" + time + ",\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8 count=" + STO.getTrackedObjectsObjects().size());
        TrackStat TS = new TrackStat(time,STO.getTrackedObjectsObjects().size());
        System.out.println("Adding tracks stats with track count\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C :"+ TS.getNumOfTracks());
        stats.updateCurrentTrackedObjects(this,TS);

    }

    private void processAndSendTrackedEvent(){
        List<StampedTrackedObjects> toRemove = new ArrayList<>();
        List<TrackedObject> validObjects = new ArrayList<>();

        for(StampedTrackedObjects STO : trackedObjects) {
            if(STO.getTime() + tracker.getFrequencey() <= time) {
                for(TrackedObject TO : STO.getTrackedObjectsObjects()) {
                    if(TO.getId().equals("ERROR")) {
                        handleSensorError(STO.getTime());
                        return;
                    }
                }
                for (TrackedObject obj : validObjects) {
                    System.out.println("TRACKED TIME DEBUG: Object " + obj.getId() +
                            " time=" + obj.getTime() +
                            " at tick=" + time);
                }
                validObjects.addAll(STO.getTrackedObjectsObjects());
                toRemove.add(STO);
            }
        }

        // Remove processed objects
        trackedObjects.removeAll(toRemove);

        // Send event only if there are objects to track
        if(!validObjects.isEmpty()) {
            sendEvent(new TrackedObjectsEvent(validObjects));
        }


    }



}