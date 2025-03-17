package bgu.spl.mics.application;

import bgu.spl.mics.application.objects.*;
import bgu.spl.mics.application.services.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

        String configPath = args[0];
        System.out.println("Starting simulation with configuration: " + configPath);

        try {
            // Step 1: Parse configuration file
            JsonObject config = parseConfig(configPath);
            String baseDir = new File(configPath).getParent();
            System.out.println("step1 check");
            // Step 2: Initialize system components
            List<Thread> serviceThreads = new ArrayList<>();
            initializeSystem(config, baseDir, serviceThreads);
            System.out.println("step2 check");
            // Step 3: Start simulation
            for (Thread thread : serviceThreads) {
                System.out.println("step 3 " + thread.getName());
                thread.start();
            }

            // Wait for all threads to finish
            for (Thread thread : serviceThreads) {
                System.out.println(thread.getName() + " get 'join'");
                thread.join();
                if (thread.isAlive()) {
                    System.err.println(thread.getName() + " is still running after join timeout.");
                } else {
                    System.out.println(thread.getName() + " End 'join'");
                }
            }

            System.out.println("end step 3");

            // Step 4: Generate output file
            FusionSlam fusionSlam = FusionSlam.getInstance();
            System.out.println("into step 4");

            generateOutputFile(fusionSlam, "output.json");

            System.out.println("Simulation completed successfully.");
        } catch (Exception e) {
            System.err.println("An error occurred during the simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parses the configuration file into a JsonObject.
     *
     * @param configPath The path to the configuration file.
     * @return Parsed JsonObject.
     */
    private static JsonObject parseConfig(String configPath) throws Exception {
        try (FileReader reader = new FileReader(configPath)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    /**
     * Initializes system components and services based on the configuration.
     *
     * @param config        The parsed configuration file as JsonObject.
     * @param baseDir       The base directory of the configuration file.
     * @param serviceThreads A list to store all service threads.
     */
    private static void initializeSystem(JsonObject config, String baseDir, List<Thread> serviceThreads) {
        System.out.println("into the initializesystem ** ");
        String cameraDataPath = Paths.get(baseDir, config.getAsJsonObject("Cameras").get("camera_datas_path").getAsString()).toString();
        String lidarDataPath = Paths.get(baseDir, config.getAsJsonObject("LiDarWorkers").get("lidars_data_path").getAsString()).toString();
        String poseDataPath = Paths.get(baseDir, config.get("poseJsonFile").getAsString()).toString();

        // CountDownLatch עבור כל השירותים מלבד TimeService
        int numServices = config.getAsJsonObject("Cameras").getAsJsonArray("CamerasConfigurations").size()
                + config.getAsJsonObject("LiDarWorkers").getAsJsonArray("LidarConfigurations").size()
                + 2; // PoseService ו-FusionSlamService
        CountDownLatch latch = new CountDownLatch(numServices);

        // Load shared components
        LiDarDataBase lidarDataBase = LiDarDataBase.getInstance(lidarDataPath);
        StatisticalFolder statsFolder = StatisticalFolder.getInstance();

        // Initialize CameraServices
        config.getAsJsonObject("Cameras").getAsJsonArray("CamerasConfigurations").forEach(cameraConfigJson -> {
            JsonObject cameraConfig = cameraConfigJson.getAsJsonObject();
            Camera camera = new Camera(
                    cameraConfig.get("id").getAsInt(),
                    cameraConfig.get("frequency").getAsInt(),
                    cameraConfig.get("camera_key").getAsString(),
                    Camera.status.UP,
                    cameraDataPath
            );
            CameraService cameraService = new CameraService(camera, latch);
            serviceThreads.add(new Thread(cameraService, cameraService.getName()));
        });

        // Initialize LiDarServices
        config.getAsJsonObject("LiDarWorkers").getAsJsonArray("LidarConfigurations").forEach(lidarConfigJson -> {
            JsonObject lidarConfig = lidarConfigJson.getAsJsonObject();
            LiDarWorkerTracker tracker = new LiDarWorkerTracker(
                    lidarConfig.get("id").getAsInt(),
                    lidarConfig.get("frequency").getAsInt()
            );
            LiDarService lidarService = new LiDarService(tracker, latch);
            serviceThreads.add(new Thread(lidarService, lidarService.getName()));
        });

        // Initialize PoseService
        GPSIMU gpsimu = new GPSIMU();
        gpsimu.loadPoseData(poseDataPath);
        PoseService poseService = new PoseService(gpsimu, poseDataPath, latch);
        serviceThreads.add(new Thread(poseService, poseService.getName()));

        // Initialize FusionSlamService
        FusionSlamService fusionSlamService = new FusionSlamService(latch);
        serviceThreads.add(new Thread(fusionSlamService, fusionSlamService.getName()));

        // Initialize TimeService
        int tickTime = config.get("TickTime").getAsInt();
        int duration = config.get("Duration").getAsInt();
        TimeService timeService = new TimeService(tickTime, duration, latch);//להכפיל ב1000 ticktime
        serviceThreads.add(new Thread(timeService, timeService.getName()));
    }


    /**
     * Generates the output file based on the data in FusionSlam.
     *
     * @param fusionSlam    The FusionSlam instance.
     * @param outputFilePath The path to the output file.
     */
    private static void generateOutputFile(FusionSlam fusionSlam, String outputFilePath) throws Exception {
        try (FileWriter writer = new FileWriter(outputFilePath)) {
            new Gson().toJson(fusionSlam.generateOutput(), writer);
        }
        System.out.println("Output file created: " + outputFilePath);
    }
}
