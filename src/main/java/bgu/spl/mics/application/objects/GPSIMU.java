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
                int time = ((Double) entry.get("time")).intValue();
                float x = ((Double) entry.get("x")).floatValue();
                float y = ((Double) entry.get("y")).floatValue();
                float yaw = ((Double) entry.get("yaw")).floatValue();
                Pose pose = new Pose(x, y, yaw, time);
                this.PoseList.add(pose); // הוספת ה-Pose לרשימה
            }
        } catch (IOException e) {
            System.err.println("Failed to load pose data: " + e.getMessage());
        }
    }
}
