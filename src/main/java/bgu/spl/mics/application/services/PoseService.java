package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.PoseEvent;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.GPSIMU;
import bgu.spl.mics.application.objects.Pose;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * PoseService is responsible for maintaining the robot's current pose (position and orientation)
 * and broadcasting PoseEvents at every tick.
 */
public class PoseService extends MicroService {
    private final GPSIMU gpsimu;
    private int currentTick;

    /**
     * Constructor for PoseService.
     *
     * @param gpsimu The GPSIMU object that provides the robot's pose data.
     * @param jsonFilePath The path to the JSON file containing pose data.
     */
    public PoseService(GPSIMU gpsimu, String jsonFilePath) {
        super("PoseService");
        this.gpsimu = gpsimu;
        this.gpsimu.loadPoseData(jsonFilePath); // טעינת המידע ל-GPSIMU
        this.currentTick = 0;
    }

    /**
     * Initializes the PoseService.
     * Subscribes to TickBroadcast and sends PoseEvents at every tick based on the current pose.
     */
    @Override
    protected void initialize() {
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
            updateOutputWithPoses(); // עדכון המידע בקובץ output.json
        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            terminate();
        });
    }

    /**
     * Updates the output.json file with all poses up to the current tick.
     */
    private void updateOutputWithPoses() {
        String outputFilePath = "output.json";
        try (FileReader reader = new FileReader(outputFilePath)) {
            // Load the existing JSON file
            Gson gson = new Gson();
            JsonObject output = JsonParser.parseReader(reader).getAsJsonObject();

            // הוספת ה-Poses עד לרגע הנוכחי
            List<Pose> posesUpToNow = gpsimu.getPoseList().stream()
                    .filter(p -> p.getTime() <= currentTick)
                    .collect(Collectors.toList());

            output.add("poses", gson.toJsonTree(posesUpToNow));

            // Write the updated JSON back to the file
            try (FileWriter writer = new FileWriter(outputFilePath)) {
                gson.toJson(output, writer);
            }
            System.out.println("Poses updated in " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Failed to update poses: " + e.getMessage());
        }
    }
}
