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
    String key;
    private status status;
    private List<StampedDetectedObjects> stampdetectedObjects;

    public Camera(int id, int frequency, String key, status status, String FilePath) {
        this.id = id;
        this.frequency = frequency;
        this.key = key;
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

            // Debugging: Print all available keys to verify
            System.out.println("Available Keys in JSON: " + data.keySet());
            System.out.println("Current Camera Key: " + key);

            if (data.containsKey(key)) {
                stampdetectedObjects = new ArrayList<>(); // Reset the list before adding new objects
                System.out.println("נכנס לתנאי בcamera");
                // Loop through each entry in the JSON for this camera
                for (StampedDetectedObjects jsonEntry : data.get(key)) {
                    // Step 1: Create a new StampedDetectedObjects instance with the correct time
                    StampedDetectedObjects a = new StampedDetectedObjects(jsonEntry.getTime());
                    System.out.println("יצר מערך של DetectedObjects");
                    // Step 2: Populate detected objects list manually
                    for (DetectedObject jsonObject : jsonEntry.getDetectedObjects()) {
                        System.out.println("האם נכנס בכלל ללולאה הזאת ????");
                        DetectedObject detectedObj = new DetectedObject(jsonObject.getId(), jsonObject.getDescription());
                        a.getDetectedObjects().add(detectedObj); // Add to the list inside "a"
                        System.out.println("הוסיף לרשימה a את הDetectedObject הרלוונטי");
                    }

                    // Step 3: Add "a" to the camera's list
                    stampdetectedObjects.add(a);
                    System.out.println("עדכן את כל הstampedDetectedObject");
                }

                // Debugging: Print loaded data
                System.out.println("LALALALALALALALALALLALALALALALALAA");
                for (StampedDetectedObjects obj : stampdetectedObjects) {
                    System.out.println("Time: " + obj.getTime());
                    for (DetectedObject amk : obj.getDetectedObjects()) {
                        System.out.println("Detected Object -> ID: " + amk.getId() + ", Description: " + amk.getDescription());
                    }
                }
            } else {
                System.out.println("No data found for camera: " + key);
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
