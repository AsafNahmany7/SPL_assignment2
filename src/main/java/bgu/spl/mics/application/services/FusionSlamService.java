package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.PoseEvent;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.messages.TrackedObjectsEvent;
import bgu.spl.mics.application.objects.FusionSlam;
import bgu.spl.mics.application.objects.LandMark;
import bgu.spl.mics.application.objects.Pose;
import bgu.spl.mics.application.objects.TrackedObject;


import java.util.List;
import java.util.stream.Collectors;

/**
 * FusionSlamService integrates data from multiple sensors to build and update
 * the robot's global map.
 */
public class FusionSlamService extends MicroService {
    private final FusionSlam fusionSlam;
    private Pose currentPose; // Current robot pose

    /**
     * Constructor for FusionSlamService.
     */
    public FusionSlamService() {
        super("FusionSlamService");
        this.fusionSlam = FusionSlam.getInstance();
        this.currentPose = null;
    }

    /**
     * Initializes the FusionSlamService.
     * Registers the service to handle TrackedObjectsEvents, PoseEvents, and TickBroadcasts,
     * and sets up callbacks for updating the global map.
     */
    @Override
    protected void initialize() {
        // Subscribe to TickBroadcast
        subscribeBroadcast(TickBroadcast.class, tick -> {
            // לחשוב אם צריך להכניס כאן פעולות
        });

        // Subscribe to TrackedObjectsEvent
        subscribeEvent(TrackedObjectsEvent.class, trackedEvent -> {
            if (currentPose != null) {
                List<LandMark> trackedLandmarks = trackedEvent.getTrackedObjects()
                        .stream()
                        .map(TrackedObject::toLandMark)
                        .collect(Collectors.toList());

                fusionSlam.processTrackedObjects(trackedLandmarks, currentPose);
            }
        });



        // Subscribe to PoseEvent
        subscribeEvent(PoseEvent.class, poseEvent -> {
            this.currentPose = poseEvent.getPose();
        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            terminate();
        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("FusionSlamService received crash notification from: " + crashed.getServiceName());
        });
    }
}
