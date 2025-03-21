package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.FusionSlam;
import bgu.spl.mics.application.objects.Pose;
import bgu.spl.mics.application.objects.TrackedObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * FusionSlamService integrates data from multiple sensors to build and update
 * the robot's global map.
 */
public class FusionSlamService extends MicroService {
    private final FusionSlam fusionSlam;
    private final CountDownLatch latch;

    /**
     * Constructor for FusionSlamService.
     */
    public FusionSlamService(CountDownLatch latch) {
        super("FusionSlamService");
        this.fusionSlam = FusionSlam.getInstance();
        this.latch = latch;
    }

    @Override
    protected void initialize() {
        System.out.println("fusionslamser initialize");

        // קבלת TrackedObjectEvent -> שמירת האובייקטים ברשימת המתנה או עיבוד ישיר
        subscribeEvent(TrackedObjectsEvent.class, trackedEvent -> {
            List<TrackedObject> trackedObjectsCopy = new ArrayList<>(trackedEvent.getTrackedObjects());//change1
            for (TrackedObject trackedObject : trackedObjectsCopy) {
                fusionSlam.addTrackedObject(trackedObject);
            }
        });

        // קבלת PoseEvent -> הוספת הפוזיציה לרשימה וטיפול ב-TrackedObjects שממתינים
        subscribeEvent(PoseEvent.class, poseEvent -> {
            fusionSlam.processPose(poseEvent.getPose());
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
