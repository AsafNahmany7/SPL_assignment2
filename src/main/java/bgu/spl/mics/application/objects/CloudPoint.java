package bgu.spl.mics.application.objects;

/**
 * CloudPoint represents a specific point in a 3D space as detected by the LiDAR.
 * These points are used to generate a point cloud representing objects in the environment.
 */
public class CloudPoint {
    private Double x;
    private Double y;

    public CloudPoint(Double x, Double y) {
        this.x = x;
        this.y = y;
    }
    public Double getX() {
        return x;
    }
    public Double getY() {
        return y;
    }
    public void setX(Double x) {
        this.x = x;
    }
    public void setY(Double y) {
        this.y = y;
    }
}
