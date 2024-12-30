package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the fusion of sensor data for simultaneous localization and mapping (SLAM).
 * Combines data from multiple sensors (e.g., LiDAR, camera) to build and update a global map.
 * Implements the Singleton pattern to ensure a single instance of FusionSlam exists.
 */
public class FusionSlam {
    // Singleton instance holder
    private List<LandMark> landmarks;
    private List<Pose> poses;

    private static class FusionSlamHolder {
        private static final FusionSlam INSTANCE = new FusionSlam();

    }
    private FusionSlam() {
        this.landmarks = new ArrayList<>(landmarks);
        this.poses = new ArrayList<>(poses);
    }

    public static FusionSlam getInstance() {
        return FusionSlamHolder.INSTANCE;
    }

    private List<LandMark> getLandmarks() {
        return landmarks;
    }
    private List<Pose> getPoses() {
        return poses;
    }
    private void addPose(Pose pose) {
        this.poses.add(pose);
    }
    private void addLandmark(LandMark landmark) {
        this.landmarks.add(landmark);
    }
}
