package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.*;

import java.util.ArrayList;
import java.util.List;

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
    int time;


    public LiDarService(LiDarWorkerTracker LiDarWorkerTracker) {
        super("LidarWorker " + LiDarWorkerTracker.getId());
        tracker = LiDarWorkerTracker;
        DetectedObjectsbyTime = new ArrayList<>();
    }




    /**
     * Initializes the LiDarService.
     * Registers the service to handle DetectObjectsEvents and TickBroadcasts,
     * and sets up the necessary callbacks for processing data.
     */
    @Override
    protected void initialize() {
        subscribeBroadcast(TickBroadcast.class,(TickBroadcast tick)->{
            time = tick.getCurrentTick();


            for(StampedDetectedObjects detectedObject : DetectedObjectsbyTime ){
                if(detectedObject.getTime() + tracker.getFrequencey() == time){
                    for(DetectedObject currentDetectedObject : detectedObject.getDetectedObjects()  ){
                        //Send the events to complete....




                        TrackedObjectsEvent trackedEvent = new TrackedObjectsEvent(currentDetectedObject.);
                        sendEvent();

                    }
                }
            }
        });

        subscribeBroadcast(DetectObjectsEvent.class,(DetectObjectsEvent detectedOBJevent)-> {
            StampedDetectedObjects ObjectsToProcess = detectedOBJevent.getDetectedObjects();
            LiDarDataBase dataBase = LiDarDataBase.getInstance();
            int time =  ObjectsToProcess.getTime();
            StampedCloudPoints cloudpoint = dataBase.getStampedCloudByTime(time);









        });

        subscribeBroadcast(TerminatedBroadcast.class,(TerminatedBroadcast broadcast)->{
            terminate();
        });

        subscribeBroadcast(CrashedBroadcast.class,(CrashedBroadcast broadcast)->{
            System.out.println("CameraService received crash notification from: " + broadcast.getServiceName());
        });
    }
}
