package bgu.spl.mics.application.messages;

import bgu.spl.mics.Event;
import bgu.spl.mics.application.objects.TrackedObject;

import java.util.List;

/**
 * Represents an event sent by LiDAR Worker to Fusion-SLAM.
 */
public class TrackedObjectsEvent implements Event<Void> {
    private final int time;
    private final List<TrackedObject> trackedObjects;

    public TrackedObjectsEvent(int time, List<TrackedObject> trackedObjects) {
        this.time = time;
        this.trackedObjects = trackedObjects;
    }

    public int getTime() {
        return time;
    }

    public List<TrackedObject> getTrackedObjects() {
        return trackedObjects;
    }
}
