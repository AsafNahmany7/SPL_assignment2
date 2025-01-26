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
import java.util.concurrent.CountDownLatch;

/**
 * FusionSlamService integrates data from multiple sensors to build and update
 * the robot's global map.
 */
public class FusionSlamService extends MicroService {
    private final FusionSlam fusionSlam;
    private Pose currentPose; // Current robot pose
    private final CountDownLatch latch;

    /**
     * Constructor for FusionSlamService.
     */
    public FusionSlamService(CountDownLatch latch) {
        super("FusionSlamService");
        this.fusionSlam = FusionSlam.getInstance();
        this.currentPose = null;
        this.latch = latch;
    }

    /**
     * Initializes the FusionSlamService.
     * Registers the service to handle TrackedObjectsEvents, PoseEvents, and TickBroadcasts,
     * and sets up callbacks for updating the global map.
     */
    @Override
    protected void initialize() {
        System.out.println("fusionslamser initialize");
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
                //למנוע מקרה קיצון שמקבל את הtracked ולא את הpose, ויקבל את הpose הרלוונטי רק בסיבוב הבא
                if(currentPose.getTime() == trackedEvent.getTime()) {  /////יכול להיות שצריך לעשות synchronized פה ??
                    fusionSlam.processTrackedObjects(trackedLandmarks, currentPose);
                }
            }
        });



        // Subscribe to PoseEvent
        subscribeEvent(PoseEvent.class, poseEvent -> {
            this.currentPose = poseEvent.getPose();
        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println("fusionslamser receive terminated ----------------");
            this.terminate();
            System.out.println("fusionslamser after receive terminated ----------??????");
        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("FusionSlamService received crash notification from: " + crashed.getServiceName());
        });
        latch.countDown();
        System.out.println("fusionslamser End initialized ]]]]]]]]]]");
    }
}
