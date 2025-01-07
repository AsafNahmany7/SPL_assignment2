package bgu.spl.mics.application.objects;


import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
/**
 * Represents a camera sensor on the robot.
 * Responsible for detecting objects in the environment.
 */
public class Camera {

    public enum status {
        UP,
        DOWN,
        ERROR
    }


    private final int id;
    private final int frequency;
    private status status;
    private List<StampedDetectedObjects> stampdetectedObjects;

    public Camera(int id, int frequency, status status, String FilePath) {
        this.id = id;
        this.frequency = frequency;
        this.status = status;
        stampdetectedObjects = new ArrayList<>();
        loadDetectedObjectsFromJson(FilePath);
    }
    public int getId() {
        return id;
    }
    public int getFrequency() {
        return frequency;
    }
    public status getStatus() {
        return status;
    }
    public List<StampedDetectedObjects> getDetectedObjects() {
        return stampdetectedObjects;
    }
    public void setStatus(status newStatus) {
        this.status = newStatus;
    }


    public void loadDetectedObjectsFromJson(String filePath) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            Type type = new TypeToken<Map<String, List<StampedDetectedObjects>>>() {}.getType();
            Map<String, List<StampedDetectedObjects>> data = gson.fromJson(reader, type);
            // טוען את המידע למבנה הנתונים של המצלמה
            String cameraKey = "camera" + id;
            if (data.containsKey(cameraKey)) {
                stampdetectedObjects = data.get(cameraKey);
            } else {
                System.out.println("No data found for camera ID: " + id);
            }
        } catch (IOException e) {
            System.err.println("Failed to load JSON: " + e.getMessage());
        }
    }

    public StampedDetectedObjects detectObjectsAtTime(int currentTime) {

        if (status != status.UP) {
            return null;
        }
        // חיפוש אובייקטים שזוהו בטיק הנוכחי
        return stampdetectedObjects.stream()
                .filter(stampdetectedObject -> stampdetectedObject.getTime() == currentTime)
                .findFirst()
                .orElse(null);
    }




}
