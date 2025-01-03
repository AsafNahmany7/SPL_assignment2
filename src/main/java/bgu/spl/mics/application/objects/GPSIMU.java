package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the robot's GPS and IMU system.
 * Provides information about the robot's position and movement.
 */
public class GPSIMU {

    public enum status {
        UP,
        DOWN,
        ERROR
    }

    private int currentTick;
    private status status;
    private List<Pose> PoseList;

    public GPSIMU() {
        currentTick = 0;
        status = status.UP;
        List<Pose> PoseList = new ArrayList<Pose>();
    }

    public int getCurrentTick() {
        return currentTick;
    }
    public void setCurrentTick(int currentTick) {
        this.currentTick = currentTick;
    }
    public status getStatus() {

        return status;
    }
    public void setStatus(status status) {
        this.status = status;
    }
    public List<Pose> getPoseList() {
        return PoseList;
    }
    public void setPoseList(Pose pose) {
        this.PoseList.add(pose);
    }
}
