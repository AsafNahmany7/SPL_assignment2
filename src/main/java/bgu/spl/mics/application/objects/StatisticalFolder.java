package bgu.spl.mics.application.objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds statistical information about the system's operation.
 * This class aggregates metrics such as the runtime of the system,
 * the number of objects detected and tracked, and the number of landmarks identified.
 */
public class StatisticalFolder {
    private int systemRuntime;
    private int numDetectedObjects;
    private int numTrackedObjects;
    private int numLandmarks;


    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public int getSystemRuntime() {
        lock.readLock().lock();
        try {
            return systemRuntime;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void incrementNumDetectedObjects() {
        lock.writeLock().lock();
        try {
            numDetectedObjects++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void incrementNumTrackedObjects() {
        lock.writeLock().lock();
        try {
            numTrackedObjects++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void incrementNumLandmarks() {
        lock.writeLock().lock();
        try {
            numLandmarks++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getNumDetectedObjects() {
        lock.readLock().lock();
        try {
            return numDetectedObjects;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNumTrackedObjects() {
        lock.readLock().lock();
        try {
            return numTrackedObjects;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNumLandmarks() {
        lock.readLock().lock();
        try {
            return numLandmarks;
        } finally {
            lock.readLock().unlock();
        }
    }

}
