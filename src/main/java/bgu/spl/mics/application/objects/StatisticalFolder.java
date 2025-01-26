package bgu.spl.mics.application.objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
