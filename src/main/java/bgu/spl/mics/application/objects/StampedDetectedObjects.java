package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents objects detected by the camera at a specific timestamp.
 * Includes the time of detection and a list of detected objects.
 */
public class StampedDetectedObjects {
    private final int time;
    private List<DetectedObject> DetectedObjects;

    public StampedDetectedObjects(int time,String id) {
        this.time = time;
        DetectedObjects = new ArrayList<>();
    }

    public List<DetectedObject> getDetectedObjects() {

        return DetectedObjects;
    }

    public int getTime() {

        return time;
    }

    public void addDetectedObject(DetectedObject DetectedObject) {
        if(DetectedObjects == null)
            return;
        if(!DetectedObjects.contains(DetectedObject))
            DetectedObjects.add(DetectedObject);
    }



}
