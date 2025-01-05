package bgu.spl.mics.application.services;

import bgu.spl.mics.Callback;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.DetectObjectsEvent;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.Camera;
import bgu.spl.mics.application.objects.StampedDetectedObjects;

/**
 * CameraService is responsible for processing data from the camera and
 * sending DetectObjectsEvents to LiDAR workers.
 * This service interacts with the Camera object to detect objects and updates
 * the system's StatisticalFolder upon sending its observations.
 */
public class CameraService extends MicroService {
    private final Camera camera;

    /**
     * Constructor for CameraService.
     *
     * @param camera The Camera object that this service will use to detect objects.
     */
    public CameraService(Camera camera) {
        super("CameraService" + camera.getId());
        this.camera = camera;
    }

    /**
     * Initializes the CameraService.
     * Registers the service to handle TickBroadcasts and sets up callbacks for sending
     * DetectObjectsEvents.
     */
    @Override
    protected void initialize() {
        // טעינת נתוני JSON עבור המצלמה
        camera.loadDetectedObjectsFromJson("path/to/camera_data.json"); //**לשים filepath גנרי??***

        // הרשמה לטיק
        subscribeBroadcast(TickBroadcast.class, tick -> {
            if (camera.getStatus() == Camera.status.UP) {
                if (camera.getFrequency() < tick.getCurrentTick()) {
                    StampedDetectedObjects detectedObjects = camera.detectObjectsAtTime(tick.getCurrentTick() - camera.getFrequency());
                    if (detectedObjects != null && !detectedObjects.getDetectedObjects().isEmpty()) {
                        sendEvent(new DetectObjectsEvent(detectedObjects, camera.getFrequency()));
                    }
                }
            }
        });

        // הרשמה ל-TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            terminate();
        });

        // הרשמה ל-CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());
        });
    }

}
