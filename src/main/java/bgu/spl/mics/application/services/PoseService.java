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

    /**
     * Constructor for PoseService.
     *
     * @param gpsimu The GPSIMU object that provides the robot's pose data.
     * @param jsonFilePath The path to the JSON file containing pose data.
     */
    public PoseService(GPSIMU gpsimu, String jsonFilePath, CountDownLatch latch) {
        super("PoseService");
        this.gpsimu = gpsimu;
        this.gpsimu.loadPoseData(jsonFilePath);
        this.currentTick = 0;
        this.latch = latch;
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
            // קבלת ה-Pose הנוכחי מתוך GPSIMU
            List<Pose> poseList = gpsimu.getPoseList();
            Pose currentPose = poseList.stream()
                    .filter(p -> p.getTime() == currentTick)
                    .findFirst()
                    .orElse(null);

            if (currentPose != null) {
                // שליחת PoseEvent עם ה-Pose הנוכחי
                sendEvent(new PoseEvent(currentPose));
            }
        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("PoseService received crash notification from: " + crashed.getServiceName());
            updateOutputWithPoses(); // עדכון המידע ב-FusionSlam
            terminate();
        });

// Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println("poseser receive terminated ----------------");
            terminate();
            System.out.println("poseser receive terminated ----------??????");
        });
        latch.countDown();
    }

/**
 * Updates the FusionSlam output with all poses up to the current tick.
 */
        private void updateOutputWithPoses() {
            FusionSlam fusionSlam = FusionSlam.getInstance();

            // איסוף ה-poses עד הטיק הנוכחי
            List<Pose> poseArray = gpsimu.getPoseList().stream()
                    .filter(p -> p.getTime() <= currentTick)
                    .collect(Collectors.toList());

// יצירת JsonArray מהמיקומים
            JsonArray posesJsonArray = new Gson().toJsonTree(poseArray).getAsJsonArray();

// יצירת JsonObject שמכיל את המידע
            JsonObject posesJsonObject = new JsonObject();
            posesJsonObject.add("poses", posesJsonArray);

// עדכון הנתונים ב-FusionSlam
            fusionSlam.updateOutput("poses", posesJsonObject);

        }
}

