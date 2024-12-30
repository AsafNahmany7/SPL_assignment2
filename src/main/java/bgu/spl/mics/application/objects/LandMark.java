package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a landmark in the environment map.
 * Landmarks are identified and updated by the FusionSlam service.
 */
public class LandMark {
    private String id;
    private String Description;
    private List<CloudPoint> Coordinates;

    public LandMark(String id, String description) {
        this.id = id;
        Description = description;
        Coordinates = new ArrayList<CloudPoint>();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getDescription() {
        return Description;
    }
    public void setDescription(String description) {
        Description = description;
    }
    public List<CloudPoint> getCoordinates() {
        return Coordinates;
    }
    public void setCoordinates(List<CloudPoint> coordinates) {
        Coordinates = coordinates;
    }


}
