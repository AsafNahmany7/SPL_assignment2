package bgu.spl.mics.application.objects;


import java.util.ArrayList;
import java.util.List;

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

    public void addDetectedObject(StampedDetectedObjects detectedObject) {
        if (detectedObject == null)
            return;
        if (!detectedObjects.contains(detectedObject))
            detectedObjects.add(detectedObject);
    }






}
