package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        this.landmarks = new ArrayList<>();
        this.poses = new ArrayList<>();
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

    /////לבדוק
    public synchronized void processTrackedObjects(List<LandMark> trackedLandmarks, Pose pose) {
        for (LandMark landmark : trackedLandmarks) {
            // המרת הקואורדינטות למערכת הגלובלית
            List<CloudPoint> globalCoordinates = landmark.getCoordinates().stream()
                    .map(localPoint -> transformToGlobal(pose, localPoint)) // שימוש בפונקציה המעודכנת
                    .collect(Collectors.toList());

            if (!landmarks.contains(landmark)) {
                landmark.setCoordinates(globalCoordinates); // Landmark חדש
                addLandmark(landmark);
            } else {
                // Landmark קיים - עדכון ממוצע
                LandMark existingLandmark = landmarks.get(landmarks.indexOf(landmark));
                List<CloudPoint> updatedCoordinates = calculateAverage(existingLandmark.getCoordinates(), globalCoordinates);
                existingLandmark.setCoordinates(updatedCoordinates);
            }
        }
        addPose(pose); // עדכון המיקום הנוכחי של הרובוט
    }


    /////לבדוק
    public CloudPoint transformToGlobal(Pose pose, CloudPoint localPoint) {
        double thetaRad = Math.toRadians(pose.getYaw()); // המרה לרדיאנים
        double cosTheta = Math.cos(thetaRad);
        double sinTheta = Math.sin(thetaRad);

        double xGlobal = cosTheta * localPoint.getX() - sinTheta * localPoint.getY() + pose.getX();
        double yGlobal = sinTheta * localPoint.getX() + cosTheta * localPoint.getY() + pose.getY();

        return new CloudPoint(xGlobal, yGlobal); // יצירת נקודה חדשה בקורדינטות הגלובליות
    }

    /////לבדוק
    private List<CloudPoint> calculateAverage(List<CloudPoint> existing, List<CloudPoint> incoming) {
        List<CloudPoint> averagedCoordinates = new ArrayList<>();
        int size = Math.min(existing.size(), incoming.size());
        for (int i = 0; i < size; i++) {
            double avgX = (existing.get(i).getX() + incoming.get(i).getX()) / 2.0;
            double avgY = (existing.get(i).getY() + incoming.get(i).getY()) / 2.0;
            averagedCoordinates.add(new CloudPoint(avgX, avgY));
        }
        return averagedCoordinates;
    }



}
