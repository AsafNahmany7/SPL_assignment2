package bgu.spl.mics.application.messages;

import bgu.spl.mics.Event;
import bgu.spl.mics.application.objects.StampedDetectedObjects;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an event sent by the Camera to detect objects.
 */
public class DetectObjectsEvent implements Event<Boolean> {
    private final StampedDetectedObjects detectedObjects;
    private final AtomicInteger detectedionTime;

    public DetectObjectsEvent(StampedDetectedObjects detectedObjects, int detectionTime) {
        this.detectedObjects = detectedObjects;
        this.detectedionTime = new AtomicInteger(detectionTime);
    }
    public int getDetectionTime() {
        return detectedionTime.get();
    }
    public StampedDetectedObjects getDetectedObjects() {
        return detectedObjects;
    }
}
