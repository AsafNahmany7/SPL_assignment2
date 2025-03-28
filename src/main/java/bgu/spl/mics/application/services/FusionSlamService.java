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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FusionSlamService integrates data from multiple sensors to build and update
 * the robot's global map.
 */
public class FusionSlamService extends MicroService {
    private final FusionSlam fusionSlam;
    private final CountDownLatch latch;
    private List<MicroService> listofMicroServices;
    private StatisticalFolder stats = StatisticalFolder.getInstance();


    /**
     * Constructor for FusionSlamService.
     */
    public FusionSlamService(CountDownLatch latch) {
        super("FusionSlamService");
        this.fusionSlam = FusionSlam.getInstance();
        this.latch = latch;
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


            System.out.println("fusionslamser got terminate from ----------------⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽⚽"+ terminated.getServiceName());
            System.out.println("Service class: " + terminated.getServiceClass().getName());
            System.out.println(terminated.getServiceClass().equals(LiDarService.class));

           if(ServicesDown()){
               StatisticalFolder stats = StatisticalFolder.getInstance();

               if(isSystemErrorFlagRaised()){
                   stats.SumDetectedObjectsWithTimeLimit(fusionSlam.getCrashTime().get());
                   stats.SumTrackedObjectsWithTimeLimit(fusionSlam.getCrashTime().get());
                   generateERROROutput();
               }
               else{
                   stats.SumDetectedObjectsRegular();
                   stats.SumTrackedObjectsRegular();
                   generateFinalOutput();
               }

               terminate();
               sendBroadcast(new TerminatedBroadcast(getName(), FusionSlamService.class,this));
           }

        });

        // Subscribe to CrashedBroadcast
        subscribeBroadcast(CrashedBroadcast.class, crashed -> {
            System.out.println("FusionSlamService received crash notification from: " + crashed.getServiceName());


            if(ServicesDown()){
                StatisticalFolder stats = StatisticalFolder.getInstance();
                stats.SumDetectedObjectsWithTimeLimit(fusionSlam.getCrashTime().get());
                stats.SumTrackedObjectsWithTimeLimit(fusionSlam.getCrashTime().get());
                generateERROROutput();
                terminate();
                sendBroadcast(new TerminatedBroadcast(this.getName(), FusionSlamService.class,this));
            }


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

            // Create the root JSON object
            JsonObject output = new JsonObject();
            StatisticalFolder stats = StatisticalFolder.getInstance();

            // Add statistics
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

            // Extract error details from fusionSlam data
            JsonObject outputData = fusionSlam.getOutputData();

            // Get the StatisticalFolder instance
            StatisticalFolder stats = StatisticalFolder.getInstance();

            // Default to current runtime, but prefer error time if available
            int errorTime = stats.getSystemRuntime();

            // Get error information from errorDetails
            if (outputData.has("errorDetails")) {
                JsonObject errorDetails = outputData.getAsJsonObject("errorDetails");

                // Extract error time if available
                if (errorDetails.has("errorTime")) {
                    errorTime = errorDetails.get("errorTime").getAsInt();
                }

                // Add error message and faultySensor directly to the root
                if (errorDetails.has("error")) {
                    output.add("error", errorDetails.get("error"));
                }
                if (errorDetails.has("faultySensor")) {
                    output.add("faultySensor", errorDetails.get("faultySensor"));
                }

                // Add camera frame if available
                if (errorDetails.has("lastCamerasFrame")) {
                    output.add("lastCamerasFrame", errorDetails.get("lastCamerasFrame"));
                }
            }

            // Add LiDAR frames if available - note the field name change
            if (outputData.has("lastLiDARFrame")) {
                output.add("lastLiDarWorkerTrackersFrame", outputData.get("lastLiDARFrame"));
            }

            // Add poses if available
            if (outputData.has("poses")) {
                JsonObject posesObj = outputData.getAsJsonObject("poses");
                if (posesObj.has("poses")) {
                    output.add("poses", posesObj.get("poses"));
                }
            }

            // Create statistics object
            JsonObject statsJson = new JsonObject();
            statsJson.addProperty("systemRuntime", errorTime);  // Use error time here
            statsJson.addProperty("numDetectedObjects", stats.getNumDetectedObjects());
            statsJson.addProperty("numTrackedObjects", stats.getNumTrackedObjects());
            statsJson.addProperty("numLandmarks", stats.getNumLandmarks());

            // Add landmarks to statistics object
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

            // Add landmarks to statistics
            statsJson.add("landMarks", landmarksObject);

            // Add statistics to output
            output.add("statistics", statsJson);

            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(output, writer);
            }

            System.out.println("Error output file created: " + outputPath);
        } catch (Exception e) {
            System.err.println("FusionSlamService: Error generating error output: " + e.getMessage());
            e.printStackTrace();
        }
    }




}
