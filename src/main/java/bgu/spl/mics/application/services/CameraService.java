package bgu.spl.mics.application.services;

import bgu.spl.mics.Callback;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.DetectObjectsEvent;
import bgu.spl.mics.application.messages.CrashedBroadcast;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.Camera;
import bgu.spl.mics.application.objects.StampedDetectedObjects;
import bgu.spl.mics.application.objects.StatisticalFolder;

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
                    StampedDetectedObjects stampdetectedObjects = camera.detectObjectsAtTime(tick.getCurrentTick() - camera.getFrequency());
                    if (stampdetectedObjects != null && !stampdetectedObjects.getDetectedObjects().isEmpty()) {

                        StatisticalFolder statFolder = StatisticalFolder.getInstance();
                        int numbersOfObjects = stampdetectedObjects.getDetectedObjects().size();
                        statFolder.setNumDetectedObjects(numbersOfObjects);

                        sendEvent(new DetectObjectsEvent(stampdetectedObjects, camera.getFrequency()));
                    }
                }
            }
        });

        // הרשמה ל-TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            camera.setStatus(Camera.status.DOWN);
            terminate();
        });

        // הרשמה ל-CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("CameraService received crash notification from: " + crashed.getServiceName());
        });
    }

}
