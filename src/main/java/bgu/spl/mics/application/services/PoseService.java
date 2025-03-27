package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.PoseEvent;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.GPSIMU;
import bgu.spl.mics.application.objects.Pose;
import bgu.spl.mics.application.objects.FusionSlam;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * PoseService is responsible for maintaining the robot's current pose (position and orientation)
 * and broadcasting PoseEvents at every tick.
 */
public class PoseService extends MicroService {
    private final GPSIMU gpsimu;
    private int currentTick;
    private final CountDownLatch latch;
    private int maxTime;

    /**
     * Constructor for PoseService.
     *
     * @param gpsimu The GPSIMU object that provides the robot's pose data.
     * @param jsonFilePath The path to the JSON file containing pose data.
     */
    public PoseService(GPSIMU gpsimu, String jsonFilePath, CountDownLatch latch) {
        super("PoseService");
        this.gpsimu = gpsimu;
        // Don't load pose data here - it should already be loaded
        this.currentTick = 0;
        this.latch = latch;
        // Find max pose time
        for (Pose pose : gpsimu.getPoseList()) {
            if (pose.getTime() > maxTime) {
                maxTime = pose.getTime();
            }
        }
        System.out.println(maxTime+ "🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆🍆"); // חציל בגאווה
    }

    /**
     * Initializes the PoseService.
     * Subscribes to TickBroadcast and sends PoseEvents at every tick based on the current pose.
     */
    @Override
    protected void initialize() {
        System.out.println("poseser initialize");
        // Subscribe to TickBroadcast
        // In PoseService.java, add to the TickBroadcast handler:
        subscribeBroadcast(TickBroadcast.class, tick -> {
            currentTick = tick.getCurrentTick();
            this.time = currentTick;
            System.out.println("DEBUG-POSE: Processing tick " + currentTick);

            Pose currentPose = null;
            for (Pose pose : gpsimu.getPoseList()) {
                if (pose.getTime() == currentTick) {
                    currentPose = pose;
                    System.out.println("DEBUG-POSE: Found pose at time " + currentTick +
                            ": x=" + currentPose.getX() +
                            ", y=" + currentPose.getY() +
                            ", yaw=" + currentPose.getYaw());
                    break;
                }
            }

            if (currentPose != null) {
                System.out.println("DEBUG-POSE: Sending PoseEvent for time " + currentTick);
                sendEvent(new PoseEvent(currentPose));
            } else {
                System.out.println("DEBUG-POSE: No pose found for time " + currentTick);
            }
        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("PoseService received crash notification from: " + crashed.getServiceName());
            updateOutputWithPoses(); // עדכון המידע ב-FusionSlam
            terminate();
            sendBroadcast(new TerminatedBroadcast(getName(), PoseService.class,this));

        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
           if(terminated.getServiceClass()!=null && terminated.getServiceClass()== TimeService.class) {
               System.out.println("PoseService received terminated notification from: " + terminated.getServiceName());
               updateOutputWithPoses();
               terminate();
               sendBroadcast(new TerminatedBroadcast(this.getName(), PoseService.class,this));

           }

           if(terminated.getServiceClass()!=null && terminated.getServiceClass()== FusionSlamService.class) {
               updateOutputWithPoses();
               terminate();
               sendBroadcast(new TerminatedBroadcast(this.getName(), PoseService.class,this));

           }
        });
        latch.countDown();
    }

/**
 * Updates the FusionSlam output with all poses up to the current tick.
 */

    private void updateOutputWithPoses() {
        FusionSlam fusionSlam = FusionSlam.getInstance();
        // עדכון הרשימה של ה-poses בפלט
        fusionSlam.updatePosesOutput(currentTick);
    }
}

