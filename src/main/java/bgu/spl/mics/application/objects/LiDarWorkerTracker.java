package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * LiDarWorkerTracker is responsible for managing a LiDAR worker.
 * It processes DetectObjectsEvents and generates TrackedObjectsEvents by using data from the LiDarDataBase.
 * Each worker tracks objects and sends observations to the FusionSlam service.
 */
public class LiDarWorkerTracker {

    public enum status {
        UP,
        DOWN,
        ERROR
    }

    private int id;
    private int frequencey;
    private status status;
    private List<TrackedObject> lastTrackedObjects;


    public LiDarWorkerTracker(int id, int frequencey,status status) {
        this.id = id;
        this.frequencey = frequencey;
        this.status = status;
        lastTrackedObjects = new ArrayList<>();
    }
    public int getId() {
        return id;
    }
    public int getFrequencey() {
        return frequencey;
    }
    public status getStatus() {
        return status;
    }
    public List<TrackedObject> getLastTrackedObjects() {
        return lastTrackedObjects;
    }
    public void addTrackedObject(TrackedObject trackedObject) {
        if(trackedObject == null)
            return;
        if(!lastTrackedObjects.contains(trackedObject))
            lastTrackedObjects.add(trackedObject);
    }

}