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
    private List<StampedDetectedObjects> detectedObjects;

    public Camera(int id, int frequency, status status) {
        this.id = id;
        this.frequency = frequency;
        this.status = status;
        detectedObjects = new ArrayList<>();
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
        return detectedObjects;
    }

    public void setStatus(status newStatus) {
        this.status = newStatus;
    }

    //*****לא בטוח שצריך את השיטה הזאת אחרי שהוספתי את loadDetectedObjectsFromJson....*****

    public void addDetectedObject(StampedDetectedObjects detectedObject) {
        if (detectedObject == null)
            return;
        if (!detectedObjects.contains(detectedObject))
            detectedObjects.add(detectedObject);
    }

    public void loadDetectedObjectsFromJson(String filePath) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            Type type = new TypeToken<Map<String, List<StampedDetectedObjects>>>() {}.getType();
            Map<String, List<StampedDetectedObjects>> data = gson.fromJson(reader, type);
            // טוען את המידע למבנה הנתונים של המצלמה
            String cameraKey = "camera" + id;
            if (data.containsKey(cameraKey)) {
                detectedObjects = data.get(cameraKey);
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
        return detectedObjects.stream()
                .filter(detectedObject -> detectedObject.getTime() == currentTime)
                .findFirst()
                .orElse(null);
    }




}
