import bgu.spl.mics.application.objects.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FusionSlamTest {
    private FusionSlam fusionSlam;

    @BeforeEach
    public void setUp() {
        fusionSlam = FusionSlam.getInstance();
        fusionSlam.reset(); // Reset state before each test
    }


    @Test
    public void testAddTrackedObject() {
        // Clear any existing landmarks
        fusionSlam.reset();

        System.out.println("Initial landmarks count: " + fusionSlam.getLandmarks().size());

        // Create a tracked object with a unique name
        List<CloudPoint> coordinates = new ArrayList<>();
        coordinates.add(new CloudPoint(1.0, 2.0));
        coordinates.add(new CloudPoint(3.0, 4.0));

        // Use a unique name that won't conflict with other tests
        TrackedObject trackedObject = new TrackedObject("UniqueTestObject" + System.currentTimeMillis(),
                10, "Test Description", coordinates);

        System.out.println("Created tracked object with ID: " + trackedObject.getId());

        // Create a pose to process with the tracked object
        Pose pose = new Pose(5.0f, 6.0f, 0.0f, 10);

        // Process pose first, then add tracked object
        fusionSlam.processPose(pose);
        System.out.println("Processed pose at time: " + pose.getTime());

        fusionSlam.addTrackedObject(trackedObject);
        System.out.println("Added tracked object at time: " + trackedObject.getTime());

        // Check if a landmark was created
        List<LandMark> landmarks = fusionSlam.getLandmarks();
        System.out.println("Final landmarks count: " + landmarks.size());

        for (LandMark landmark : landmarks) {
            System.out.println("Found landmark with ID: " + landmark.getId());
        }

        assertEquals(1, landmarks.size(), "Should have created one landmark");
    }

    @Test
    public void testTransformToGlobal() {
        Pose pose = new Pose(5.0f, 6.0f, 90.0f, 10);
        CloudPoint localPoint = new CloudPoint(1.0, 2.0);

        CloudPoint globalPoint = fusionSlam.transformToGlobal(pose, localPoint);

        // Calculate expected values for 90-degree rotation
        double yawRad = Math.toRadians(90);
        double expectedX = 1.0 * Math.cos(yawRad) - 2.0 * Math.sin(yawRad) + 5.0;
        double expectedY = 1.0 * Math.sin(yawRad) + 2.0 * Math.cos(yawRad) + 6.0;

        assertEquals(expectedX, globalPoint.getX(), 0.0001);
        assertEquals(expectedY, globalPoint.getY(), 0.0001);
    }

    @Test
    public void testProcessPose() {
        // Create a pose
        Pose pose = new Pose(5.0f, 6.0f, 0.0f, 10);

        // Add pose
        fusionSlam.processPose(pose);

        // Create tracked objects with the same time
        List<CloudPoint> coordinates = new ArrayList<>();
        coordinates.add(new CloudPoint(1.0, 2.0));

        TrackedObject trackedObject = new TrackedObject("TestObject", 10, "Test Description", coordinates);

        // Add tracked object
        fusionSlam.addTrackedObject(trackedObject);

        // Add another tracked object with the same ID to test averaging
        List<CloudPoint> coordinates2 = new ArrayList<>();
        coordinates2.add(new CloudPoint(3.0, 4.0));

        TrackedObject trackedObject2 = new TrackedObject("TestObject", 10, "Test Description", coordinates2);

        // Add second tracked object
        fusionSlam.addTrackedObject(trackedObject2);

        // Check if a single landmark was created (not two)
        List<LandMark> landmarks = fusionSlam.getLandmarks();
        assertEquals(1, landmarks.size());

        // Check if the coordinates were averaged
        LandMark landmark = landmarks.get(0);
        List<CloudPoint> avgCoordinates = landmark.getCoordinates();

        // Expected average of (6,8) and (8,10) is (7,9)
        assertEquals(7.0, avgCoordinates.get(0).getX(), 0.0001);
        assertEquals(9.0, avgCoordinates.get(0).getY(), 0.0001);
    }

    @Test
    public void testUpdatePosesOutput() {
        // Create poses
        Pose pose1 = new Pose(1.0f, 2.0f, 0.0f, 5);
        Pose pose2 = new Pose(3.0f, 4.0f, 0.0f, 10);
        Pose pose3 = new Pose(5.0f, 6.0f, 0.0f, 15);

        // Add poses
        fusionSlam.processPose(pose1);
        fusionSlam.processPose(pose2);
        fusionSlam.processPose(pose3);

        // Update poses output up to tick 10
        fusionSlam.updatePosesOutput(10);

        // Get the output data
        JsonObject outputData = fusionSlam.getOutputData();

        // Check that poses are included up to tick 10
        assertTrue(outputData.has("poses"));

        // Verify the structure of the poses data
        JsonObject posesObj = outputData.getAsJsonObject("poses");
        assertTrue(posesObj.has("poses"));

        // Verify that only poses up to tick 10 are included
        JsonArray posesArray = posesObj.getAsJsonArray("poses");
        assertEquals(2, posesArray.size()); // Should have poses for time 5 and 10
    }

    @Test
    public void testGenerateOutput() {
        // Add some test data
        List<CloudPoint> coordinates = new ArrayList<>();
        coordinates.add(new CloudPoint(1.0, 2.0));

        TrackedObject trackedObject = new TrackedObject("TestLandmark", 10, "Test Description", coordinates);
        Pose pose = new Pose(5.0f, 6.0f, 0.0f, 10);

        fusionSlam.processPose(pose);
        fusionSlam.addTrackedObject(trackedObject);

        // Generate output
        JsonObject output = fusionSlam.generateOutput();

        // Verify output structure
        assertTrue(output.has("poses"));
        assertTrue(output.has("statistics"));
        assertTrue(output.has("landMarks"));

        // Verify statistics are present
        JsonObject stats = output.getAsJsonObject("statistics");
        assertTrue(stats.has("systemRuntime"));
        assertTrue(stats.has("numDetectedObjects"));
        assertTrue(stats.has("numTrackedObjects"));
        assertTrue(stats.has("numLandmarks"));

        // Verify landmarks contain the test landmark
        JsonObject landMarks = output.getAsJsonObject("landMarks");
        assertTrue(landMarks.has("TestLandmark"));
    }

    @Test
    public void testUpdateOutput() {
        // Test updating arbitrary output data
        JsonObject testData = new JsonObject();
        testData.addProperty("testKey", "testValue");

        fusionSlam.updateOutput("testSection", testData);

        // Verify the data was added
        JsonObject outputData = fusionSlam.getOutputData();
        assertTrue(outputData.has("testSection"));

        JsonObject section = outputData.getAsJsonObject("testSection");
        assertEquals("testValue", section.get("testKey").getAsString());
    }
}