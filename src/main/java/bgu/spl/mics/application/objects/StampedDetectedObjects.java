package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.SerializedName;

/**
 * Represents objects detected by the camera at a specific timestamp.
 * Includes the time of detection and a list of detected objects.
 */
public class StampedDetectedObjects {
    private final int time;
    @SerializedName("detectedObjects") // בשביל הgson
    private List<DetectedObject> DetectedObjects;

    public StampedDetectedObjects(int time) {
        this.time = time;
        DetectedObjects = new ArrayList<>();
    }

    public List<DetectedObject> getDetectedObjects() {

        if (DetectedObjects == null) {
            DetectedObjects = new ArrayList<>(); // Ensure it's never null
        }
        return DetectedObjects;
    }

    public int getTime() {

        return time;
    }

    public void addDetectedObject(DetectedObject DetectedObject) { //אם משתמש בזה מתישהו אז לשים synchronized
        if(DetectedObjects == null)
            return;
        if(!DetectedObjects.contains(DetectedObject))
            DetectedObjects.add(DetectedObject);
    }



}
