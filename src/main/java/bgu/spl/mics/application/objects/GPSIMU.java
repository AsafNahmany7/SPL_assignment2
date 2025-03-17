package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
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
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(jsonFilePath)) {
            Type type = new TypeToken<Map<String, Object>[]>() {}.getType();
            Map<String, Object>[] rawData = gson.fromJson(reader, type);
            for (Map<String, Object> entry : rawData) {
                // ✅ Debugging: Print raw entry before processing
                System.out.println("\n🔹 Processing Pose Entry: " + entry);

                // Extract and validate time
                Object timeObj = entry.get("time");
                if (!(timeObj instanceof Double)) {
                    System.err.println("  ❌ ERROR: Invalid time format -> " + timeObj);
                    continue;
                }
                int time = ((Double) timeObj).intValue();

                // Extract and validate x, y, yaw
                float x = extractFloat(entry, "x");
                float y = extractFloat(entry, "y");
                float yaw = extractFloat(entry, "yaw");

                // ✅ Debugging: Print extracted values
                System.out.println("  ✅ Extracted Pose -> Time: " + time + ", X: " + x + ", Y: " + y + ", Yaw: " + yaw);

                // Create Pose object
                Pose pose = new Pose(x, y, yaw, time);
                this.PoseList.add(pose); // הוספת ה-Pose לרשימה
            }
        } catch (IOException e) {
            System.err.println("Failed to load pose data: " + e.getMessage());
        }
    }

    // ✅ Helper function to safely extract float values
    private float extractFloat(Map<String, Object> entry, String key) {
        Object obj = entry.get(key);
        if (obj instanceof Double) {
            return ((Double) obj).floatValue();
        } else {
            System.err.println("  ❌ ERROR: Invalid " + key + " format -> " + obj);
            return 0.0f; // Default value in case of error
        }
    }

}
