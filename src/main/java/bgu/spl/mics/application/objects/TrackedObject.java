package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an object tracked by the LiDAR.
 * This object includes information about the tracked object's ID, description, 
 * time of tracking, and coordinates in the environment.
 */
public class TrackedObject {

    private final String id;
    private final int  time;
    private final String description;
    private final List<CloudPoint> coordinates;

    public TrackedObject(String id, int time, String description, List<CloudPoint> coordinates) {
        this.id = id;
        this.time = time;
        this.description = description;
        this.coordinates = coordinates;
    }
    public String getId() {
        return id;
    }
    public int getTime() {
        return time;
    }
    public String getDescription() {
        return description;
    }
    public List<CloudPoint> getCoordinates() {
        return coordinates;
    }

    ///////לבדוק
    public LandMark toLandMark() {
        LandMark landmark = new LandMark(this.id, this.description);
        landmark.setCoordinates(new ArrayList<>(this.coordinates)); // מעתיקים את הקואורדינטות
        return landmark;
    }
}
