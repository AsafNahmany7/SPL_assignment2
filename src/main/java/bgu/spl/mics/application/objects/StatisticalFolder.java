package bgu.spl.mics.application.objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds statistical information about the system's operation.
 * This class aggregates metrics such as the runtime of the system,
 * the number of objects detected and tracked, and the number of landmarks identified.
 */
public class StatisticalFolder {
    private final AtomicInteger systemRuntime = new AtomicInteger(0);
    private final AtomicInteger numDetectedObjects = new AtomicInteger(0);
    private final AtomicInteger numTrackedObjects = new AtomicInteger(0);
    private final AtomicInteger numLandmarks = new AtomicInteger(0);

    public void incrementSystemRuntime() {
        systemRuntime.incrementAndGet();
    }

    public void incrementNumDetectedObjects() {
        numDetectedObjects.incrementAndGet();
    }

    public void incrementNumTrackedObjects() {
        numTrackedObjects.incrementAndGet();
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
    }

}
