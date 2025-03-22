package bgu.spl.mics.application.objects;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a single pose entry from the JSON configuration.
 */
public class PoseJson {
    @SerializedName("time")
    private int time;

    @SerializedName("x")
    private float x;

    @SerializedName("y")
    private float y;

    @SerializedName("yaw")
    private float yaw;

    // Default constructor for Gson
    public PoseJson() {}

    // Constructor
    public PoseJson(int time, float x, float y, float yaw) {
        this.time = time;
        this.x = x;
        this.y = y;
        this.yaw = yaw;
    }

    // Getters and setters
    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    // Convert to Pose object
    public Pose toPose() {
        return new Pose(x, y, yaw, time);
    }
}