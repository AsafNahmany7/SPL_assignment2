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
    private final CountDownLatch latch;//לא למחוק


    public LiDarService(LiDarWorkerTracker tracker, CountDownLatch latch) {
        super("LidarWorker " + tracker.getId());
        this.tracker = tracker;// לא למחוק
        DetectedObjectsbyTime = new ArrayList<>();
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
            System.out.println("התקבל בlidarServ טיק מס': " + tick.getCurrentTick());
            int time = tick.getCurrentTick();
            LiDarDataBase database = LiDarDataBase.getInstance();
            StampedDetectedObjects toProcess = null;
            int detectionTime = 0;

            System.out.println("יש כרגע בDetectedObjectsbyTime כמות StampedDetectedObjects של: " + DetectedObjectsbyTime.size());
            for(StampedDetectedObjects currentTimeDetectedObjects : DetectedObjectsbyTime ){
                detectionTime = currentTimeDetectedObjects.getTime();
                System.out.println("הdetectionTime הוא: " + detectionTime);
                System.out.println("הtracker.getFrequencey() הוא: " + tracker.getFrequencey());
                System.out.println("ביחד הם: " + (detectionTime + tracker.getFrequencey()));
                System.out.println("כאשר הtime הוא: " + time);
                if(detectionTime + tracker.getFrequencey() <= time ){
                    System.out.println("לכן הצליחו להיכנס לתנאי");
                    toProcess = currentTimeDetectedObjects;
                }else{
                    System.out.println("לכן *לא* הצליחו להיכנס לתנאי");
                }
            }

            if(toProcess!= null){
                System.out.println(" הtoProcess שונה מnull ולכן נכנס לתנאי");
                List<TrackedObject> trackedObjects = new ArrayList<>();

                for(DetectedObject currentDetectedObject : toProcess.getDetectedObjects()){ //עם הזמן המתאים StampedDetectedObjectsה toProcess.getDetectedObjects קיבל את ה
                    String id = currentDetectedObject.getId();
                    System.out.println(" הid של הcurrentDetectedObject הוא: " + id);
                    StampedCloudPoints correspondingCloudPoints = database.searchStampedClouds(detectionTime,id);

                    List<CloudPoint> cloudpoints = new ArrayList<>();

                    for(List<Double>   coordinates  : correspondingCloudPoints.getCloudPoints()){
                        cloudpoints.add(new CloudPoint(coordinates.get(0),coordinates.get(1)));
                        System.out.println("מכניס לcloudpoints את הקורדינטות: " + coordinates.get(0) + " , " + coordinates.get(1));
                    }
                    System.out.println("-----ניצור TrackedObject-----");
                    TrackedObject newTrackedObject = new TrackedObject(id,time,currentDetectedObject.getDescription(),cloudpoints);
                    trackedObjects.add(newTrackedObject);
                    System.out.println(" hopa hopa lidar"+tracker.getId()+" add tracked object "+ newTrackedObject.getId()+" at beginning process time: "+ time +" at current tick time: "+ tick.getCurrentTick());

                }
                TrackedObjectsEvent output = new TrackedObjectsEvent(trackedObjects);
                sendEvent(output);

            }

        });

        //DetectedObjectEvent callback

        subscribeEvent(DetectObjectsEvent.class,(DetectObjectsEvent objEvent)->{
            System.out.println(this.getName() + " קיבל DetectedObject ");
           StampedDetectedObjects stampedDetectedObjects = objEvent.getDetectedObjects();

           for(DetectedObject dodo : stampedDetectedObjects.getDetectedObjects()){
               System.out.println("popa popa lidar"+tracker.getId()+" recieved "+dodo.getId()+" at time : "+ stampedDetectedObjects.getTime());
           }

            DetectedObjectsbyTime.add(stampedDetectedObjects);
        });

        //terminate callback
        subscribeBroadcast(TerminatedBroadcast.class,(TerminatedBroadcast broadcast)->{
            System.out.println(getName() + " received terminated broadcast.");
            tracker.setStatus(LiDarWorkerTracker.status.DOWN);
            terminate();
        });

        //crash callback
        subscribeBroadcast(CrashedBroadcast.class,(CrashedBroadcast broadcast)->{
            System.out.println("lidarser received crash notification from: " + broadcast.getServiceName());
        });

        latch.countDown();//לא למחוק
        System.out.println("lidarser End initialized ]]]]]]]]]]");//לא למחוק
    }
}
