package bgu.spl.mics.application;

import bgu.spl.mics.application.objects.*;
import bgu.spl.mics.application.services.*;
import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * The main entry point for the GurionRock Pro Max Ultra Over 9000 simulation.
 */
public class GurionRockRunner {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        if (args.length == 0) {
            System.err.println("Configuration file path is required as the first argument.");
            return;
        }

        try {
            // Parse the configuration file
            String configPath = args[0];
            File configFile = new File(configPath);
            File dataFolder = configFile.getParentFile();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JSONinput input;

            try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
                input = gson.fromJson(reader, JSONinput.class);
                System.out.println("JSON successfully parsed into objects!");
            }

            // Load all data sources
            List<Camera> cameras = loadCameras(input, dataFolder);
            List<LiDarWorkerTracker> lidars = loadLiDARs(input, dataFolder);
            GPSIMU gpsimu = loadGPSIMU(input, dataFolder);

            // Now you have all data loaded and can create services
            // [Next steps for creating services would go here]
            // Initialize services and run the simulation
            runSimulation(cameras, lidars, gpsimu, input.getTickTime(), input.getDuration());











        } catch (Exception e) {
            System.err.println("Error in simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private static void runSimulation(List<Camera> cameras, List<LiDarWorkerTracker> lidars,
                                      GPSIMU gpsimu, int tickTime, int duration) {

        System.out.println("\n===== CREATING SERVICES =====");

        // Calculate total number of services (for the CountDownLatch)
        int numServices = cameras.size() + lidars.size() + 2; // +2 for PoseService and FusionSlamService
        CountDownLatch latch = new CountDownLatch(numServices);

        List<Thread> serviceThreads = new ArrayList<>();

        // Create camera services
        System.out.println("Creating camera services...");
        for (Camera camera : cameras) {
            CameraService cameraService = new CameraService(camera, latch);
            Thread thread = new Thread(cameraService, cameraService.getName());
            serviceThreads.add(thread);
            System.out.println("Created service: " + cameraService.getName());
        }

        // Create LiDAR services
        System.out.println("Creating LiDAR services...");
        for (LiDarWorkerTracker tracker : lidars) {
            LiDarService lidarService = new LiDarService(tracker, latch);
            Thread thread = new Thread(lidarService, lidarService.getName());
            serviceThreads.add(thread);
            System.out.println("Created service: " + lidarService.getName());
        }

        // Create pose service
        System.out.println("Creating pose service...");
        String posePath = ""; // Not actually used in the service since we've already loaded the data
        PoseService poseService = new PoseService(gpsimu, posePath, latch);
        Thread poseThread = new Thread(poseService, poseService.getName());
        serviceThreads.add(poseThread);
        System.out.println("Created service: " + poseService.getName());

        // Create fusion slam service
        System.out.println("Creating fusion slam service...");
        FusionSlamService fusionSlamService = new FusionSlamService(latch);
        Thread fusionSlamThread = new Thread(fusionSlamService, fusionSlamService.getName());
        serviceThreads.add(fusionSlamThread);
        System.out.println("Created service: " + fusionSlamService.getName());

        // Create time service (starts last)
        System.out.println("Creating time service...");
        TimeService timeService = new TimeService(tickTime, duration, latch);
        Thread timeThread = new Thread(timeService, timeService.getName());
        serviceThreads.add(timeThread);
        System.out.println("Created service: " + timeService.getName());

        // Start all services
        System.out.println("\n===== STARTING SIMULATION =====");
        for (Thread thread : serviceThreads) {
            System.out.println("Starting " + thread.getName());
            thread.start();
        }

        // Wait for all threads to complete
        System.out.println("Waiting for all services to complete...");
        try {
            for (Thread thread : serviceThreads) {
                thread.join();
                System.out.println(thread.getName() + " has completed");
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for services to complete");
            e.printStackTrace();
        }

        // Generate output
        System.out.println("\n===== GENERATING OUTPUT =====");
        FusionSlam fusionSlam = FusionSlam.getInstance();
        try {
            generateOutputFile(fusionSlam, "output.json");
        } catch (Exception e) {
            System.err.println("Error generating output file: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\nSimulation completed successfully!");
    }

    /**
     * Generates the output file from the FusionSlam instance
     */
    private static void generateOutputFile(FusionSlam fusionSlam, String outputPath) throws Exception {
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
    }

    /**
     * Loads and initializes Camera objects from configuration
     *
     * @param input The parsed configuration input
     * @param dataFolder The folder containing data files
     * @return List of initialized Camera objects
     */
    private static List<Camera> loadCameras(JSONinput input, File dataFolder) throws IOException {
        System.out.println("\n===== LOADING CAMERAS =====");

        // Extract camera configuration
        CamerasConfigurations camerasConfigurations = input.getCameras();
        CameraJson[] cameraConfigs = camerasConfigurations.getCamerasConfigurations();
        String cameraDataPath = camerasConfigurations.getCameraDatasPath();

        // Create absolute path for camera data file
        File cameraDataFile = new File(dataFolder, cameraDataPath);
        String absoluteCameraDataPath = cameraDataFile.getAbsolutePath();
        System.out.println("Camera Data Path: " + absoluteCameraDataPath);

        // Load camera data
        Map<String, List<StampedDetectedObjects>> allCameraData = loadCameraData(absoluteCameraDataPath);
        System.out.println("Data loaded for " + allCameraData.size() + " cameras");

        // Create a list to hold all camera instances
        List<Camera> cameras = new ArrayList<>();

        // Create a Camera instance for each configuration
        for (CameraJson config : cameraConfigs) {
            // Create a new Camera
            Camera camera = new Camera(
                    config.getId(),
                    config.getFrequency(),
                    config.getCameraKey(),
                    Camera.status.UP
            );

            // Add pre-loaded detected objects data to this camera
            String cameraKey = config.getCameraKey();
            List<StampedDetectedObjects> cameraData = allCameraData.get(cameraKey);

            if (cameraData != null) {
                // Add all the detected objects to this camera
                for (StampedDetectedObjects sdo : cameraData) {
                    camera.getDetectedObjects().add(sdo);
                }
            }

            // Add the camera to our list
            cameras.add(camera);

            System.out.println("Created Camera: ID=" + config.getId() +
                    ", Key=" + config.getCameraKey() +
                    ", with " + (cameraData != null ? cameraData.size() : 0) + " time entries");
        }

        return cameras;
    }

    /**
     * Loads and initializes LiDAR worker trackers from configuration
     *
     * @param input The parsed configuration input
     * @param dataFolder The folder containing data files
     * @return List of initialized LiDarWorkerTracker objects
     */
    private static List<LiDarWorkerTracker> loadLiDARs(JSONinput input, File dataFolder) throws IOException {
        System.out.println("\n===== LOADING LIDAR CONFIGURATIONS =====");

        // Get LiDAR configurations
        LidarConfigurations lidarConfig = input.getLiDarWorkers();
        LiDarJson[] lidarConfigs = lidarConfig.getLiDarWorkers();
        String lidarDataPath = lidarConfig.getLidars_data_path();

        System.out.println("Found " + lidarConfigs.length + " LiDAR configurations");

        // Create a list to hold all LiDAR instances
        List<LiDarWorkerTracker> lidars = new ArrayList<>();

        // Create a LiDar instance for each configuration
        for (LiDarJson config : lidarConfigs) {
            // Create a new LiDar with the configuration parameters
            LiDarWorkerTracker lidar = new LiDarWorkerTracker(
                    config.getId(),
                    config.getFrequency()
            );

            // Add the new LiDar instance to the list
            lidars.add(lidar);
        }

        // Get the absolute path for lidar_data.json
        File lidarDataFile = new File(dataFolder, lidarDataPath);
        String absoluteLidarDataPath = lidarDataFile.getAbsolutePath();
        System.out.println("LiDAR Data Path: " + absoluteLidarDataPath);

        // Initialize LiDarDataBase with the absolute path
        try {
            System.out.println("\n===== INITIALIZING LIDAR DATABASE =====");
            LiDarDataBase database = LiDarDataBase.getInstance(absoluteLidarDataPath);
            System.out.println("✅ LiDarDataBase initialized successfully!");

            // Print some info about the loaded data
            List<StampedCloudPoints> cloudPoints = database.getStampedCloud();
            System.out.println("Loaded " + cloudPoints.size() + " cloud point entries");

            // Print info about LiDAR worker trackers
            for (LiDarWorkerTracker lidar : lidars) {
                System.out.println("LiDAR " + lidar.getId() + " is ready with frequency: " + lidar.getFrequencey());
            }
        } catch (Exception e) {
            System.err.println("Error initializing LiDAR database: " + e.getMessage());
            e.printStackTrace();
        }

        return lidars;
    }

    /**
     * Loads and initializes GPSIMU from configuration
     *
     * @param input The parsed configuration input
     * @param dataFolder The folder containing data files
     * @return Initialized GPSIMU object
     */
    private static GPSIMU loadGPSIMU(JSONinput input, File dataFolder) throws IOException {
        System.out.println("\n===== INITIALIZING GPSIMU =====");

        // Get pose data path
        String poseDataPath = input.getPoseJsonFile();

        // Create absolute path for pose data file
        File poseDataFile = new File(dataFolder, poseDataPath);
        String absolutePoseDataPath = poseDataFile.getAbsolutePath();
        System.out.println("Pose Data Path: " + absolutePoseDataPath);

        // Initialize GPSIMU with the absolute path
        GPSIMU gpsimu = new GPSIMU();

        try {
            gpsimu.loadPoseData(absolutePoseDataPath);

            // Print some info about the loaded pose data
            List<Pose> poses = gpsimu.getPoseList();
            System.out.println("✅ GPSIMU initialized successfully!");
            System.out.println("Loaded " + poses.size() + " pose entries");

            // Example: print first few poses
            int count = Math.min(3, poses.size());
            for (int i = 0; i < count; i++) {
                Pose pose = poses.get(i);
                System.out.println("Pose " + i + ": Time=" + pose.getTime() +
                        ", Position=(" + pose.getX() + ", " + pose.getY() +
                        "), Yaw=" + pose.getYaw());
            }
        } catch (Exception e) {
            System.err.println("Error initializing GPSIMU: " + e.getMessage());
            e.printStackTrace();
        }

        return gpsimu;
    }

    /**
     * Loads camera data from the JSON file.
     *
     * @param filePath Path to the camera data JSON file
     * @return Map of camera keys to their detected objects
     */
    private static Map<String, List<StampedDetectedObjects>> loadCameraData(String filePath) throws IOException {
        System.out.println("Loading camera data from: " + filePath);
        Gson gson = new Gson();
        Map<String, List<StampedDetectedObjects>> result = new HashMap<>();

        try (Reader reader = Files.newBufferedReader(Paths.get(filePath))) {
            // Parse JSON structure
            JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();

            // Process each camera entry
            for (String cameraKey : rootObject.keySet()) {
                System.out.println("Processing data for camera key: " + cameraKey);
                List<StampedDetectedObjects> cameraData = new ArrayList<>();
                JsonArray timeEntries = rootObject.getAsJsonArray(cameraKey);

                // Process each time entry
                for (JsonElement timeEntry : timeEntries) {
                    JsonObject entry = timeEntry.getAsJsonObject();
                    int time = entry.get("time").getAsInt();

                    // Create a new stamped detected objects for this time
                    StampedDetectedObjects sdo = new StampedDetectedObjects(time);

                    // Process detected objects
                    JsonArray objects = entry.getAsJsonArray("detectedObjects");
                    for (JsonElement objElement : objects) {
                        JsonObject obj = objElement.getAsJsonObject();
                        String id = obj.get("id").getAsString();
                        String description = obj.get("description").getAsString();

                        // Create detected object and add to stamped objects
                        DetectedObject detectedObj = new DetectedObject(id, description);
                        sdo.addDetectedObject(detectedObj);
                    }

                    // Add this time entry to camera data
                    cameraData.add(sdo);
                }

                // Store all data for this camera
                result.put(cameraKey, cameraData);
                System.out.println("Added " + cameraData.size() + " time entries for camera: " + cameraKey);
            }
        }

        return result;
    }
}