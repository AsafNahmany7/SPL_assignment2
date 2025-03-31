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
    private List<TrackedObject> lastFrame;
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
        this.lastFrame = new ArrayList<>();
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
            System.out.println("detected object time: " + objEvent.getDetectionTime());
            System.out.println("detected obj time of detcted: " + objEvent.getDetectedObjects().getTime());
            StampedDetectedObjects stampedDetectedObjects = objEvent.getDetectedObjects();
            LiDarDataBase dataBase = LiDarDataBase.getInstance();
            int detectionTime = objEvent.getDetectionTime();
            StampedTrackedObjects STO = new StampedTrackedObjects(detectionTime);

            boolean errorFound = false;
            for(DetectedObject detectedObject: stampedDetectedObjects.getDetectedObjects()){
                StampedCloudPoints currentCP = dataBase.searchStampedClouds(detectionTime,detectedObject.getId());

                if(currentCP.getId().equals("ERROR")){
                    errorFound=true;
                    handleSensorError(detectionTime);
                    return;
                }



                List<CloudPoint> cloudPoints = new ArrayList<>();
                for(List<Double> coordinates : currentCP.getCloudPoints()){
                    cloudPoints.add(new CloudPoint(coordinates.get(0),coordinates.get(1)));
                }
                TrackedObject TO = new TrackedObject(currentCP.getId(), detectionTime, detectedObject.getDescription(),cloudPoints);
                STO.addTrackedObject(TO);
            }
            trackedObjects.add(STO);
            if(STO.getTrackedObjectsObjects() != null) {
                lastFrame = STO.getTrackedObjectsObjects();
            }

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
        System.out.println("Error detected in LiDAR:\uD83D\uDC36\uD83D\uDC36 " + tracker.getId());

        updateLastLiDARFrame();
        // Update FusionSlam with error details
        FusionSlam fusionSlam = FusionSlam.getInstance();

        JsonObject errorDetails = new JsonObject();
        errorDetails.addProperty("error", "sensor Lidar " + tracker.getId() + " disconnected");
        errorDetails.addProperty("faultySensor", "LiDarTrackerWorker " + tracker.getId());
        errorDetails.addProperty("errorTime", detTime);  // Add the error time
        System.out.println("LiDAR error detected at time: " + detTime);

        // Add last tracked objects if available
        if (lastFrame != null && !lastFrame.isEmpty()) {
           //JsonObject lastTrackedObjectsFrame = new JsonObject();
           //JsonObject lidarData = new JsonObject();
           //lidarData.addProperty("time", lastFrame.get(0).getTime());
           //lidarData.add("trackedObjects", new Gson().toJsonTree(lastFrame));
           //lastTrackedObjectsFrame.add("LiDAR" + tracker.getId(), lidarData);
           //errorDetails.add("lastLiDARFrame", lastTrackedObjectsFrame);
        }
        else {
            System.out.println("No tracked objects available for LiDAR" + tracker.getId() + " to update.");
        }

        fusionSlam.updateOutput("errorDetails", errorDetails);

        // Mark the LiDAR as having an ERROR status
        tracker.setStatus(LiDarWorkerTracker.status.ERROR);

        System.out.println("LiDAR: " + tracker.getId() + " sending CrashedBroadcast");
        // Broadcast CrashedBroadcast to stop all services

        FusionSlam fs = FusionSlam.getInstance();
        fs.crashTime.compareAndSet(-1,detTime);
        raiseSystemErrorFlag();
        fs.setCrasherServiceClass(LiDarService.class);
        terminate();
        System.out.println("Lidar sending crash⚽");
        sendBroadcast(new CrashedBroadcast(getName(),detTime,LiDarService.class,this));

        // Terminate this service

    }



    private void updateLastLiDARFrame() {
        if (lastFrame == null || lastFrame.isEmpty()) {
            System.out.println("No tracked objects available for LiDAR" + tracker.getId() + " to update..");
            return;
        }
        System.out.println("נכנס לupdatelastlidar");
        FusionSlam fusionSlam = FusionSlam.getInstance();
        JsonObject lastLiDARFrame = new JsonObject();
        JsonObject lidarData = new JsonObject();

        lidarData.addProperty("time", lastFrame.get(0).getTime());
        System.out.println("time: " + lastFrame.get(0).getTime());
        lidarData.add("trackedObjects", new Gson().toJsonTree(lastFrame));
        lastLiDARFrame.add("LiDAR" + tracker.getId(), lidarData);
        fusionSlam.updateOutput("lastLiDARFrame", lastLiDARFrame);
    }

    private void updateStats(StampedTrackedObjects STO){
        System.out.println("Adding tracks stats: time=" + time + ",\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8\uD83D\uDEF8 count=" + STO.getTrackedObjectsObjects().size());
        TrackStat TS = new TrackStat(time,STO.getTrackedObjectsObjects().size());
        System.out.println("Adding tracks stats with track count\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C\uD83D\uDE4C :"+ TS.getNumOfTracks());
        stats.updateCurrentTrackedObjects(this,TS);

    }

    private int lastProcessedTick = -1;

    private void processAndSendTrackedEvent() {
        //if (lastProcessedTick == time) return; // למניעת כפילות בטיק הנוכחי
        //lastProcessedTick = time;

        List<StampedTrackedObjects> toRemove = new ArrayList<>();
        List<TrackedObject> validObjects = new ArrayList<>();
        List<TrackedObject> Last = null;

        for (StampedTrackedObjects STO : trackedObjects) {
            if (STO.getTime() + tracker.getFrequencey() <= time) {
                validObjects.addAll(STO.getTrackedObjectsObjects());
                toRemove.add(STO);
                Last = STO.getTrackedObjectsObjects();
            }
        }

        trackedObjects.removeAll(toRemove);

        if (!validObjects.isEmpty()) {
            FusionSlam fusionSlam = FusionSlam.getInstance();
            fusionSlam.updateLastLiDarFrame(getName(), Last);
            sendEvent(new TrackedObjectsEvent(validObjects));
        }
    }





}