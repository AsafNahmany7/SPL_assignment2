package bgu.spl.mics.application.objects;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;



/**
 * Represents the robot's GPS and IMU system.
 * Provides information about the robot's position and movement.
 */
public class GPSIMU {

    public enum status {
        UP,
        DOWN,
        ERROR
    }

    private int currentTick;
    private status status;
    private List<Pose> PoseList;

    public GPSIMU() {
        currentTick = 0;
        status = status.UP;
        PoseList = new ArrayList<>();
    }

    public List<Pose> getPoses() {
        return PoseList;
    }
    public int getCurrentTick() {
        return currentTick;
    }
    public void setCurrentTick(int currentTick) {
        this.currentTick = currentTick;
    }
    public status getStatus() {
        return status;
    }
    public void setStatus(status status) {
        this.status = status;
    }
    public List<Pose> getPoseList() {
        return PoseList;
    }
    public void setPoseList(Pose pose) {
        this.PoseList.add(pose);
    }

    public void loadPoseData(String jsonFilePath) {
        System.out.println("Loading pose data from file: " + jsonFilePath);

        File file = new File(jsonFilePath);
        if (!file.exists() || jsonFilePath.isEmpty()) {
            System.err.println("Pose data file does not exist or path is empty: " + jsonFilePath);
            createDefaultPoses();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            // Create a Gson instance
            Gson gson = new Gson();

            // Parse the JSON array directly into an array of Pose objects
            Pose[] poseArray = gson.fromJson(reader, Pose[].class);

            // Clear existing poses and add all the new ones
            PoseList.clear();
            if (poseArray != null && poseArray.length > 0) {
                for (Pose pose : poseArray) {
                    PoseList.add(pose);
                }
                System.out.println("Successfully loaded " + PoseList.size() + " poses");
            } else {
                System.err.println("No pose data found in the file");
                createDefaultPoses();
            }
        } catch (IOException e) {
            System.err.println("IO error reading pose data: " + e.getMessage());
            e.printStackTrace();
            createDefaultPoses();
        } catch (Exception e) {
            System.err.println("Error parsing pose data: " + e.getMessage());
            e.printStackTrace();
            createDefaultPoses();
        }
    }





    private float extractFloat(Map<String, Object> entry, String key) {
        if (!entry.containsKey(key)) {
            System.err.println("Warning: Entry missing '" + key + "' field");
            return 0.0f;
        }

        Object obj = entry.get(key);
        if (obj instanceof Double) {
            return ((Double) obj).floatValue();
        } else if (obj instanceof Integer) {
            return ((Integer) obj).floatValue();
        } else if (obj instanceof Float) {
            return (Float) obj;
        } else {
            System.err.println("Warning: Invalid format for '" + key + "': " + obj);
            return 0.0f;
        }

    }

    private void createDefaultPoses() {
        System.out.println("Creating default poses");
        // Clear any existing poses first
        PoseList.clear();

        // Create default poses with sequential time values and simple movement pattern
        for (int i = 1; i <= 20; i++) {
            PoseList.add(new Pose(i * 0.1f, i * 0.05f, i * 0.02f, i));
        }

        System.out.println("Created " + PoseList.size() + " default poses");
    }

    }
