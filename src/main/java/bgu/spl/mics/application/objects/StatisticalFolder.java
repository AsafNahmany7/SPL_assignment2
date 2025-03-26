package bgu.spl.mics.application.objects;
import bgu.spl.mics.Message;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.services.CameraService;
import bgu.spl.mics.application.services.LiDarService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.*;
import static java.util.Arrays.stream;

/**
 * Holds statistical information about the system's operation.
 * This class aggregates metrics such as the runtime of the system,
 * the number of objects detected and tracked, and the number of landmarks identified.
 */
public class StatisticalFolder {
    private  AtomicInteger systemRuntime ;
    private  AtomicInteger numDetectedObjects;
    private  AtomicInteger numTrackedObjects;
    private  AtomicInteger numLandmarks;

    private ConcurrentHashMap<MicroService, BlockingQueue<DetectStat>> camerasDetections = new ConcurrentHashMap<>();
    private ConcurrentHashMap<MicroService, BlockingQueue<TrackStat>> lidarsTrackings = new ConcurrentHashMap<>();





    private static class StatisticalFolderHolder {
        private static final StatisticalFolder INSTANCE = new StatisticalFolder();
    }
    public StatisticalFolder() {
        systemRuntime = new AtomicInteger(0);
        numDetectedObjects = new AtomicInteger(0);
        numTrackedObjects = new AtomicInteger(0);
        numLandmarks = new AtomicInteger(0);
    }
    public static StatisticalFolder getInstance() {
        return StatisticalFolderHolder.INSTANCE;
    }



    public void incrementSystemRuntime() {
        systemRuntime.incrementAndGet();
    }
    public void registerCameraService(CameraService service) {
        camerasDetections.putIfAbsent(service, new LinkedBlockingQueue<>());
    }
    public void registerLidarService(LiDarService service) {
        lidarsTrackings.putIfAbsent(service, new LinkedBlockingQueue<>());


    }

    public void updateCurrentDetectedObjects(MicroService microService,DetectStat DS) {
        camerasDetections.get(microService).add(DS); // מוסיף int לתור

    }

    public void updateCurrentTrackedObjects(MicroService microService,TrackStat TS) {
        lidarsTrackings.get(microService).add(TS); // מוסיף int לתור


    }


    public void SumDetectedObjectsRegular() {
        int totalSum = camerasDetections.values().stream()
                .flatMap(queue -> queue.stream()) // Stream of DetectStat objects
                .mapToInt(DetectStat::getNumOfDetections) // Extract NumOfDetections from each object
                .sum();

        numDetectedObjects.set(totalSum);
    }

    // Time-limited version for DetectStat objects
    public void SumDetectedObjectsWithTimeLimit(int timeLimit) {
        int totalSum = camerasDetections.values().stream()
                .flatMap(queue -> queue.stream()) // Stream of DetectStat objects
                .filter(detectStat -> detectStat.getTime() <= timeLimit) // Only include DetectStats with time <= timeLimit
                .mapToInt(DetectStat::getNumOfDetections) // Extract NumOfDetections from each object
                .sum();

        System.out.println("TOTAL SUM of detections \uD83C\uDF07\uD83C\uDF07\uD83C\uDF07: " + totalSum);

        numDetectedObjects.set(totalSum);
    }



    public void SumTrackedObjectsRegular() {
        int totalSum = lidarsTrackings.values().stream()
                .flatMap(queue -> queue.stream()) // Stream of TrackStat objects
                .mapToInt(TrackStat::getNumOfTracks) // Extract NumOfTracks from each object
                .sum();

        numTrackedObjects.set(totalSum);
    }

    public void SumTrackedObjectsWithTimeLimit(int timeLimit) {
        int totalSum = lidarsTrackings.values().stream()
                .flatMap(queue -> queue.stream()) // Stream of TrackStat objects
                .filter(trackStat -> trackStat.getTime() <= timeLimit) // Only include TrackStats with time <= timeLimit
                .mapToInt(TrackStat::getNumOfTracks) // Extract NumOfTracks from each object
                .sum();

        System.out.println("TOTAL SUM of tracktions \uD83C\uDF07\uD83C\uDF07\uD83C\uDF07: " + totalSum);
        numTrackedObjects.set(totalSum);
    }














    public void setNumDetectedObjects(int newValue) {
        numDetectedObjects.addAndGet(newValue);
    }

    public void setNumTrackedObjects(int newValue) {
        numTrackedObjects.addAndGet(newValue);
    }

    public void incrementNumLandmarks() {
        numLandmarks.incrementAndGet();
    }

    public int getSystemRuntime() {
        return systemRuntime.get();
    }
    public int getNumDetectedObjects() {
        return numDetectedObjects.get();
    }

    public int getNumTrackedObjects() {
        return numTrackedObjects.get();
    }

    public int getNumLandmarks() {
        return numLandmarks.get();
    }//צריך לממש עדיין את העדכון איפה שמוסיפים landmarks

}
