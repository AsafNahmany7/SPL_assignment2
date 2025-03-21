package bgu.spl.mics.application.objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Manages the fusion of sensor data for simultaneous localization and mapping (SLAM).
 * Combines data from multiple sensors (e.g., LiDAR, camera) to build and update a global map.
 * Implements the Singleton pattern to ensure a single instance of FusionSlam exists.
 */
public class FusionSlam {
    private final List<LandMark> landmarks;
    private final List<Pose> poses;
    private final List<TrackedObject> trackedObjectsQueue;
    private final JsonObject outputData;

    private final Lock posesLock;
    private final Lock landmarksLock;
    private final Lock trackedObjectsLock;
    private final Lock outputLock;

    private static class FusionSlamHolder {
        private static final FusionSlam INSTANCE = new FusionSlam();
    }

    private FusionSlam() {
        this.landmarks = new ArrayList<>();
        this.poses = new ArrayList<>();
        this.trackedObjectsQueue = new ArrayList<>();
        this.outputData = new JsonObject();

        this.posesLock = new ReentrantLock();
        this.landmarksLock = new ReentrantLock();
        this.trackedObjectsLock = new ReentrantLock();
        this.outputLock = new ReentrantLock();
    }

    public static FusionSlam getInstance() {
        return FusionSlamHolder.INSTANCE;
    }

    public void addTrackedObject(TrackedObject trackedObject) {
        posesLock.lock();
        try {
            for (Pose pose : poses) {
                if (pose.getTime() == trackedObject.getTime()) {
                    processTrackedObjects(trackedObject.toLandMark(), pose);
                    return;
                }
            }
        } finally {
            posesLock.unlock();
        }

        trackedObjectsLock.lock();
        try {
            trackedObjectsQueue.add(trackedObject);
        } finally {
            trackedObjectsLock.unlock();
        }
    }

    public void processPose(Pose newPose) {
        posesLock.lock();
        try {
            poses.add(newPose);
        } finally {
            posesLock.unlock();
        }

        trackedObjectsLock.lock();
        try {
            List<TrackedObject> matchedObjects = trackedObjectsQueue.stream()
                    .filter(tracked -> tracked.getTime() == newPose.getTime())
                    .collect(Collectors.toList());

            for (TrackedObject tracked : matchedObjects) {//change2
                processTrackedObjects(tracked.toLandMark(), newPose);
            }
            trackedObjectsQueue.removeAll(matchedObjects);
        } finally {
            trackedObjectsLock.unlock();
        }
    }

    // הוספת משתנה Set לשמירה על ה-ID-ים של ה-LandMarks
    private final Set<String> existingLandmarkIds = new HashSet<>();

    public void processTrackedObjects(LandMark trackedLandmark, Pose pose) {
        landmarksLock.lock();
        try {
            List<CloudPoint> globalCoordinates = trackedLandmark.getCoordinates().stream()
                    .map(localPoint -> transformToGlobal(pose, localPoint))
                    .collect(Collectors.toList());

            if (!existingLandmarkIds.contains(trackedLandmark.getId())) {
                System.out.println("הlandmark: " + trackedLandmark.getId() + " *לא* קיים, לכן נוסיף אותו");
                trackedLandmark.setCoordinates(globalCoordinates);
                addLandmark(trackedLandmark);
                existingLandmarkIds.add(trackedLandmark.getId()); // הוספת ה-ID למעקב
                System.out.println("גודל רשימת הlandmarks: " + landmarks.size());
                StatisticalFolder.getInstance().incrementNumLandmarks();
            } else {
                System.out.println("הlandmark: " + trackedLandmark.getId() + " כן קיים, לכן לא נוסיף אותו");
                // איתור ה-Landmark הקיים ושדרוג הקורדינטות שלו
                for (LandMark existingLandmark : landmarks) {
                    if (existingLandmark.getId().equals(trackedLandmark.getId())) {
                        List<CloudPoint> updatedCoordinates = calculateAverage(existingLandmark.getCoordinates(), globalCoordinates);
                        existingLandmark.setCoordinates(updatedCoordinates);
                        break; // ברגע שמצאנו את ה-LandMark עם ה-ID המתאים, אין צורך להמשיך לחפש
                    }
                }
            }
        } finally {
            landmarksLock.unlock();
        }
    }


    private void addLandmark(LandMark landmark) {
        landmarksLock.lock();
        try {
            this.landmarks.add(landmark);
        } finally {
            landmarksLock.unlock();
        }
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

        if (existing.size() > incoming.size()) {
            averagedCoordinates.addAll(existing.subList(sizeMin, existing.size()));
        } else {
            averagedCoordinates.addAll(incoming.subList(sizeMin, incoming.size()));
        }

        return averagedCoordinates;
    }

    public void updatePosesOutput(int upToTick) {
        List<Pose> relevantPoses;

        posesLock.lock();
        try {
            relevantPoses = poses.stream()
                    .filter(p -> p.getTime() <= upToTick)
                    .collect(Collectors.toList());
        } finally {
            posesLock.unlock();
        }

        JsonArray posesJsonArray = new Gson().toJsonTree(relevantPoses).getAsJsonArray();
        JsonObject posesJsonObject = new JsonObject();
        posesJsonObject.add("poses", posesJsonArray);

        updateOutput("poses", posesJsonObject);
    }

    public void updateOutput(String key, JsonObject value) {
        outputLock.lock();
        try {
            outputData.add(key, value);
        } finally {
            outputLock.unlock();
        }
    }

    public JsonObject generateOutput() {
        JsonObject output = new JsonObject();
        output.add("poses", generatePoseArray());
        output.add("statistics", generateStatistics());
        output.add("landMarks", generateLandmarkData());
        return output;
    }

    private JsonArray generatePoseArray() {
        JsonArray poseArray = new JsonArray();
        posesLock.lock();
        try {
            for (Pose pose : poses) {
                JsonObject poseJson = new JsonObject();
                poseJson.addProperty("time", pose.getTime());
                poseJson.addProperty("x", pose.getX());
                poseJson.addProperty("y", pose.getY());
                poseJson.addProperty("yaw", pose.getYaw());
                poseArray.add(poseJson);
            }
        } finally {
            posesLock.unlock();
        }
        return poseArray;
    }

    private JsonObject generateLandmarkData() {
        JsonObject landmarkData = new JsonObject();
        landmarksLock.lock();
        try {
            System.out.println("גודל מערך הlandmarks בהתחלה הוא: " + landmarks.size());
            for (LandMark landmark : landmarks) {
                System.out.println("עכשיו מתעסק עם landmark: " + landmark.getId());
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
        } finally {
            landmarksLock.unlock();
        }
        System.out.println("גודל הlandmarks לjson הוא: " + landmarkData.size());
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
