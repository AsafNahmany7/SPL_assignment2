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
        subscribeBroadcast(TickBroadcast.class, tick -> {
            currentTick = tick.getCurrentTick();
            this.time=currentTick;
            List<Pose> poseList = gpsimu.getPoseList();



            // Check if we've already processed the pose with the highest time value
            System.out.println("🍆🍆🍆🍆🍆🍆🍆 current ticka is :" +currentTick);
            if (currentTick > maxTime) {
                System.out.println("🚀 PoseService: All poses processed. Current tick: " +
                        currentTick + ", Max pose time: " + maxTime);
                updateOutputWithPoses();
                sendBroadcast(new TerminatedBroadcast(getName(), PoseService.class));
                terminate();
            } else{
                // Process current pose if it exists
                Pose currentPose = null;
                for (Pose pose : poseList) {
                    if (pose.getTime() == currentTick) {
                        currentPose = pose;
                        break;
                    }
                }

                if (currentPose != null) {
                    sendEvent(new PoseEvent(currentPose));
                }






            }


        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("PoseService received crash notification from: " + crashed.getServiceName());
            updateOutputWithPoses(); // עדכון המידע ב-FusionSlam
            sendBroadcast(new TerminatedBroadcast(getName(), PoseService.class));
            terminate();
        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
           if(terminated.getServiceClass()!=null && terminated.getServiceClass()== TimeService.class) {
               System.out.println("PoseService received terminated notification from: " + terminated.getServiceName());
               updateOutputWithPoses();
               sendBroadcast(new TerminatedBroadcast(this.getName(), PoseService.class));
               terminate();
           }

           if(terminated.getServiceClass()!=null && terminated.getServiceClass()== FusionSlamService.class) {
               updateOutputWithPoses();
               sendBroadcast(new TerminatedBroadcast(this.getName(), PoseService.class));
               terminate();
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

