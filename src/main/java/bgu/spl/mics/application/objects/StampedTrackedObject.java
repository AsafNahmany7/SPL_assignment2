package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

public class StampedTrackedObject {
    private final int stamp;
    private List<TrackedObject> trackedObjects;
    public StampedTrackedObject(int stamp) {
        this.stamp = stamp;
        trackedObjects = new ArrayList<TrackedObject>();
    }
    public void addTrackedObject(TrackedObject trackedObject) {
        trackedObjects.add(trackedObject);
    }
    public List<TrackedObject> getTrackedObjects() {
        return trackedObjects;
    }
    public int getStamp() {
        return stamp;
    }
}
