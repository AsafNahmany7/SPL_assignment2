package bgu.spl.mics.application.services;

import bgu.spl.mics.Future;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.*;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * LiDarService is responsible for processing data from the LiDAR sensor and
 * sending TrackedObjectsEvents to the FusionSLAM service.
 * 
 * This service interacts with the LiDarWorkerTracker object to retrieve and process
 * cloud point data and updates the system's StatisticalFolder upon sending its
 * observations.
 */
public class LiDarService extends MicroService {

    /**
     * Constructor for LiDarService.
     *
     * @param LiDarWorkerTracker A LiDAR Tracker worker object that this service will use to process data.
     */
    private LiDarWorkerTracker tracker;
    private List<StampedDetectedObjects> DetectedObjectsbyTime;
    private HashMap<String, TrackedObject> trackedObjectsMap;
    private final CountDownLatch latch;//לא למחוק
    int time;


    public LiDarService(LiDarWorkerTracker tracker, CountDownLatch latch) {
        super("LidarWorker " + tracker.getId());
        this.tracker = tracker;// לא למחוק
        DetectedObjectsbyTime = new ArrayList<>();
        trackedObjectsMap = new HashMap<>();
        this.latch = latch;//לא למחוק
    }




    /**
     * Initializes the LiDarService.
     * Registers the service to handle DetectObjectsEvents and TickBroadcasts,
     * and sets up the necessary callbacks for processing data.
     */
    @Override
    protected void initialize() {

        System.out.println("lidarser initialize");//לא למחוק

        //tick callback:
        subscribeBroadcast(TickBroadcast.class,(TickBroadcast tick)->{
            time = tick.getCurrentTick();


            for(StampedDetectedObjects detectedObject : DetectedObjectsbyTime ){
                if(detectedObject.getTime() + tracker.getFrequencey() == time){

                    List<TrackedObject> TrackedObjectsToSend = new ArrayList<>();

                    for(DetectedObject currentDetectedObject : detectedObject.getDetectedObjects()  ){
                        //Send the events to complete....
                        TrackedObject currentTrackedObject = trackedObjectsMap.get(currentDetectedObject.getId());
                        TrackedObjectsToSend.add(currentTrackedObject);

                    }
                    TrackedObjectsEvent EventToSend = new TrackedObjectsEvent(time,TrackedObjectsToSend);
                    int numberOfTrackedObjects = EventToSend.getTrackedObjects().size();
                    StatisticalFolder statsFolder = StatisticalFolder.getInstance();
                    statsFolder.setNumTrackedObjects(numberOfTrackedObjects);

                    sendEvent(EventToSend);
                    }
            }


        });

        //DetectedObjectEvent callback

        subscribeEvent(DetectObjectsEvent.class,(DetectObjectsEvent objEvent)->{
            StampedDetectedObjects DetectedObjectList = objEvent.getDetectedObjects();
            LiDarDataBase database = LiDarDataBase.getInstance();
            List<StampedCloudPoints> CloudpointsOfObjects = database.getStampedCloudsByTime(DetectedObjectList.getTime());

            for(DetectedObject current : DetectedObjectList.getDetectedObjects()  ){
                List<CloudPoint> CloudPointsOfCurrent = new ArrayList<>();
                boolean isFound = false;

                for(StampedCloudPoints currentCloudPointStamped : CloudpointsOfObjects){

                    if(currentCloudPointStamped.getId().equals(current.getId())&& !isFound){
                        isFound = true;
                        List<List<Double>> coordinates = currentCloudPointStamped.getCloudPoints();

                        for(List<Double> coordinate : coordinates){
                            CloudPoint SingularCloudOfCurrent = new CloudPoint(coordinate.get(0),coordinate.get(1));
                            CloudPointsOfCurrent.add(SingularCloudOfCurrent);
                        }
                    }

                }
                TrackedObject TrackedInstance = new TrackedObject(current.getId(),DetectedObjectList.getTime(), current.getDescription(),CloudPointsOfCurrent );
                trackedObjectsMap.put(TrackedInstance.getId(), TrackedInstance);
            }
        });

        //terminate callback
        subscribeBroadcast(TerminatedBroadcast.class,(TerminatedBroadcast broadcast)->{
            tracker.setStatus(LiDarWorkerTracker.status.DOWN);
            terminate();
        });

        //crash callback
        subscribeBroadcast(CrashedBroadcast.class,(CrashedBroadcast broadcast)->{
            System.out.println("lidarser received crash notification from: " + broadcast.getServiceName());
        });
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(getName() + " received terminated broadcast.");
            terminate();
        });
        latch.countDown();//לא למחוק
        System.out.println("lidarser End initialized ]]]]]]]]]]");//לא למחוק
    }
}
