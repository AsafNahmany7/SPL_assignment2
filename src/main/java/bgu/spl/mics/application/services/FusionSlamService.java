package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * FusionSlamService integrates data from multiple sensors to build and update
 * the robot's global map.
 */
public class FusionSlamService extends MicroService {
    private final FusionSlam fusionSlam;
    private final CountDownLatch latch;
    private boolean LiDarsFinished;
    private boolean PoseServiceDone;
    private List<MicroService> listofMicroServices;

    /**
     * Constructor for FusionSlamService.
     */
    public FusionSlamService(CountDownLatch latch) {
        super("FusionSlamService");
        this.fusionSlam = FusionSlam.getInstance();
        this.latch = latch;
        this.LiDarsFinished = false;
        this.PoseServiceDone = false;
        listofMicroServices = new ArrayList<>();
    }

    @Override
    protected void initialize() {
        System.out.println("fusionslamser initialize");

        // קבלת TrackedObjectEvent -> שמירת האובייקטים ברשימת המתנה או עיבוד ישיר
        subscribeEvent(TrackedObjectsEvent.class, trackedEvent -> {
            List<TrackedObject> trackedObjectsCopy = new ArrayList<>(trackedEvent.getTrackedObjects());//change1
            for (TrackedObject trackedObject : trackedObjectsCopy) {
                fusionSlam.addTrackedObject(trackedObject);
            }
        });

        // קבלת PoseEvent -> הוספת הפוזיציה לרשימה וטיפול ב-TrackedObjects שממתינים
        subscribeEvent(PoseEvent.class, poseEvent -> {
            fusionSlam.processPose(poseEvent.getPose());
        });

        // Subscribe to TerminatedBroadcast
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println("fusionslamser got terminate from ----------------"+ terminated.getServiceName());
           if(ServicesDown()){
               sendBroadcast(new TerminatedBroadcast(getName(), FusionSlamService.class));
               generateFinalOutput();
               terminate();
           }

        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("FusionSlamService received crash notification from: " + crashed.getServiceName());

                generateERROROutput();
                terminate();
                sendBroadcast(new TerminatedBroadcast(this.getName(), FusionSlam.class));

        });

        latch.countDown();
        System.out.println("fusionslamser End initialized ]]]]]]]]]]");
    }

    public void registerMicroService(MicroService microService) {
        listofMicroServices.add(microService);
    }

    private boolean ServicesDown(){
        for(MicroService microService : listofMicroServices){
            System.out.println(microService.getName() + " בודק אם terminated ");
            if(!microService.isTerminated()) {
                System.out.println(microService.getName() + " לא terminated עדיין ");
                return false;
            }
        }
        return true;
    }

    // Add this to FusionSlamService before it terminates
    private void generateFinalOutput() {
        System.out.println("FusionSlamService: Generating final output");

        try {
            FusionSlam fusionSlam = FusionSlam.getInstance();
            String outputPath = "output.json";

            // Get the statistics
            StatisticalFolder stats = StatisticalFolder.getInstance();

            // Create the root JSON object
            JsonObject output = new JsonObject();
            output.addProperty("systemRuntime", stats.getSystemRuntime());
            output.addProperty("numDetectedObjects", stats.getNumDetectedObjects());
            output.addProperty("numTrackedObjects", stats.getNumTrackedObjects());
            output.addProperty("numLandmarks", stats.getNumLandmarks());

            // Add the landmarks
            JsonObject landmarksObject = new JsonObject();
            for (LandMark landmark : fusionSlam.getLandmarks()) {
                JsonObject landmarkJson = new JsonObject();
                landmarkJson.addProperty("id", landmark.getId());
                landmarkJson.addProperty("description", landmark.getDescription());

                // Add coordinates
                JsonArray coordinatesArray = new JsonArray();
                for (CloudPoint point : landmark.getCoordinates()) {
                    JsonObject pointJson = new JsonObject();
                    pointJson.addProperty("x", point.getX());
                    pointJson.addProperty("y", point.getY());
                    coordinatesArray.add(pointJson);
                }
                landmarkJson.add("coordinates", coordinatesArray);

                landmarksObject.add(landmark.getId(), landmarkJson);
            }

            output.add("landMarks", landmarksObject);

            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(output, writer);
            }

            System.out.println("Output file created: " + outputPath);
        } catch (Exception e) {
            System.err.println("FusionSlamService: Error generating final output: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void generateERROROutput() {
        System.out.println("FusionSlamService: Generating ERROR output");

        try {
            FusionSlam fusionSlam = FusionSlam.getInstance();
            String outputPath = "error_output.json";
            JsonObject output = new JsonObject();

            output.add("",fusionSlam.getOutputData());//האם זה תקין ??

            // Get the statistics
            StatisticalFolder stats = StatisticalFolder.getInstance();

            // Create the root JSON object
            output.addProperty("systemRuntime", stats.getSystemRuntime());
            output.addProperty("numDetectedObjects", stats.getNumDetectedObjects());
            output.addProperty("numTrackedObjects", stats.getNumTrackedObjects());
            output.addProperty("numLandmarks", stats.getNumLandmarks());

            // Add the landmarks
            JsonObject landmarksObject = new JsonObject();
            for (LandMark landmark : fusionSlam.getLandmarks()) {
                JsonObject landmarkJson = new JsonObject();
                landmarkJson.addProperty("id", landmark.getId());
                landmarkJson.addProperty("description", landmark.getDescription());

                // Add coordinates
                JsonArray coordinatesArray = new JsonArray();
                for (CloudPoint point : landmark.getCoordinates()) {
                    JsonObject pointJson = new JsonObject();
                    pointJson.addProperty("x", point.getX());
                    pointJson.addProperty("y", point.getY());
                    coordinatesArray.add(pointJson);
                }
                landmarkJson.add("coordinates", coordinatesArray);

                landmarksObject.add(landmark.getId(), landmarkJson);
            }

            output.add("landMarks", landmarksObject);

            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(output, writer);
            }

            System.out.println("Output file created: " + outputPath);
        } catch (Exception e) {
            System.err.println("FusionSlamService: Error generating final output: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
