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

    public Camera(int id, int frequency, String key, status status) {
        this.id = id;
        this.frequency = frequency;
        this.key = key;
        this.status = status;
        stampdetectedObjects = new ArrayList<>();


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
    public boolean isEmpty() {
        return stampdetectedObjects.isEmpty();
    }

    public String getKey() {

        return key;
    }
    public void setKey(String key) {}

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

    public void removeStampedObject (StampedDetectedObjects objectToRemove){
        stampdetectedObjects.remove(objectToRemove);
    }

    public int stampedSize(){
        return stampdetectedObjects.size();
    }



}
