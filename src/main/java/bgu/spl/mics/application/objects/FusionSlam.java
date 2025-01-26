package bgu.spl.mics.application.objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

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
    private JsonObject outputData;

    private static class FusionSlamHolder {
        private static final FusionSlam INSTANCE = new FusionSlam();
    }

    private FusionSlam() {
        this.landmarks = new ArrayList<>();
        this.poses = new ArrayList<>();
        this.outputData = new JsonObject();
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

    public synchronized void processTrackedObjects(List<LandMark> trackedLandmarks, Pose pose) {
        for (LandMark landmark : trackedLandmarks) {
            // המרת הקואורדינטות למערכת הגלובלית
            List<CloudPoint> globalCoordinates = landmark.getCoordinates().stream()
                    .map(localPoint -> transformToGlobal(pose, localPoint))
                    .collect(Collectors.toList());

            if (!landmarks.contains(landmark)) {
                landmark.setCoordinates(globalCoordinates); // Landmark חדש
                addLandmark(landmark);
                StatisticalFolder statFolder = StatisticalFolder.getInstance();
                statFolder.incrementNumLandmarks();
            } else {
                // Landmark קיים - עדכון ממוצע
                LandMark existingLandmark = landmarks.get(landmarks.indexOf(landmark));
                List<CloudPoint> updatedCoordinates = calculateAverage(existingLandmark.getCoordinates(), globalCoordinates);
                existingLandmark.setCoordinates(updatedCoordinates);
            }
        }
        addPose(pose); // עדכון המיקום הנוכחי של הרובוט
    }

    public CloudPoint transformToGlobal(Pose pose, CloudPoint localPoint) {
        double thetaRad = Math.toRadians(pose.getYaw());
        double cosTheta = Math.cos(thetaRad);
        double sinTheta = Math.sin(thetaRad);

        double xGlobal = cosTheta * localPoint.getX() - sinTheta * localPoint.getY() + pose.getX();
        double yGlobal = sinTheta * localPoint.getX() + cosTheta * localPoint.getY() + pose.getY();

        return new CloudPoint(xGlobal, yGlobal);
    }

    private List<CloudPoint> calculateAverage(List<CloudPoint> existing, List<CloudPoint> incoming) {
        List<CloudPoint> averagedCoordinates = new ArrayList<>();
        int sizeMin = Math.min(existing.size(), incoming.size());
        for (int i = 0; i < sizeMin; i++) {
            double avgX = (existing.get(i).getX() + incoming.get(i).getX()) / 2.0;
            double avgY = (existing.get(i).getY() + incoming.get(i).getY()) / 2.0;
            averagedCoordinates.add(new CloudPoint(avgX, avgY));
        }

        if (existing.size() >= incoming.size()) {
            for (int j = sizeMin; j < existing.size(); j++) {
                averagedCoordinates.add(new CloudPoint(existing.get(j).getX(), existing.get(j).getY()));
            }
        } else {
            for (int j = sizeMin; j < incoming.size(); j++) {
                averagedCoordinates.add(new CloudPoint(incoming.get(j).getX(), incoming.get(j).getY()));
            }
        }

        return averagedCoordinates;
    }

    // עדכון קובץ הפלט
    public synchronized void updateOutput(String key, JsonObject value) {
        outputData.add(key, value);
    }

    // הפקת נתוני פלט
    public synchronized JsonObject generateOutput() {
        JsonObject output = new JsonObject();
        output.add("poses", generatePoseArray());
        output.add("landMarks", generateLandmarkData());
        output.add("statistics", generateStatistics());
        return output;
    }

    private JsonArray generatePoseArray() {
        JsonArray poseArray = new JsonArray();
        for (Pose pose : poses) {
            JsonObject poseJson = new JsonObject();
            poseJson.addProperty("time", pose.getTime());
            poseJson.addProperty("x", pose.getX());
            poseJson.addProperty("y", pose.getY());
            poseJson.addProperty("yaw", pose.getYaw());
            poseArray.add(poseJson);
        }
        return poseArray;
    }

    private JsonObject generateLandmarkData() {
        JsonObject landmarkData = new JsonObject();
        for (LandMark landmark : landmarks) {
            JsonObject landmarkJson = new JsonObject();
            landmarkJson.addProperty("id", landmark.getId());
            landmarkJson.addProperty("description", landmark.getDescription());

            JsonArray coordinatesArray = new JsonArray();
            for (CloudPoint point : landmark.getCoordinates()) {
                JsonObject pointJson = new JsonObject();
                pointJson.addProperty("x", point.getX());
                pointJson.addProperty("y", point.getY());
                coordinatesArray.add(pointJson);
            }
            landmarkJson.add("coordinates", coordinatesArray);
            landmarkData.add(landmark.getId(), landmarkJson);
        }
        return landmarkData;
    }

    private JsonObject generateStatistics() {
        StatisticalFolder stats = StatisticalFolder.getInstance();
        JsonObject statsJson = new JsonObject();
        statsJson.addProperty("systemRuntime", stats.getSystemRuntime());
        statsJson.addProperty("numDetectedObjects", stats.getNumDetectedObjects());
        statsJson.addProperty("numTrackedObjects", stats.getNumTrackedObjects());
        statsJson.addProperty("numLandmarks", stats.getNumLandmarks());
        return statsJson;
    }
}
