import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

import bgu.spl.mics.application.objects.Camera;
import bgu.spl.mics.application.objects.StampedDetectedObjects;
import bgu.spl.mics.application.objects.DetectedObject;

//Tests dont use reading from config files

public class cameraTests {
    private Camera camera;

    @BeforeEach
    public void setUp() {
        // Create a test camera instance with empty path so it doesn't try to load from file
        camera = new Camera(1, 5, "test-camera", Camera.status.UP);

        // Manually create and add test data
        StampedDetectedObjects sdo = new StampedDetectedObjects(1);
        sdo.addDetectedObject(new DetectedObject("obj1", "Test Object 1"));
        camera.getDetectedObjects().add(sdo);
    }

    @Test
    public void testGetId() {
        assertEquals(1, camera.getId(), "Camera ID should be 1");
    }

    @Test
    public void testGetFrequency() {
        assertEquals(5, camera.getFrequency(), "Camera frequency should be 5");
    }

    @Test
    public void testGetKey() {
        assertEquals("test-camera", camera.getKey(), "Camera key should be 'test-camera'");
    }

    @Test
    public void testGetStatus() {
        assertEquals(Camera.status.UP, camera.getStatus(), "Camera status should be UP");
    }

    @Test
    public void testSetStatus() {
        camera.setStatus(Camera.status.DOWN);
        assertEquals(Camera.status.DOWN, camera.getStatus(), "Camera status should be DOWN after setting");
    }

    @Test
    public void testDetectObjectsAtTime() {
        // Test detecting objects at time 1 (which we added in setUp)
        StampedDetectedObjects result = camera.detectObjectsAtTime(1);
        assertNotNull(result, "Should return objects for time 1");
        assertEquals(1, result.getDetectedObjects().size(), "Should have 1 detected object");
        assertEquals("obj1", result.getDetectedObjects().get(0).getId(), "Object ID should be 'obj1'");
    }

    @Test
    public void testDetectObjectsAtNonExistentTime() {
        // Test detecting objects at a time that doesn't exist
        StampedDetectedObjects result = camera.detectObjectsAtTime(99);
        assertNull(result, "Should return null for non-existent time");
    }

    @Test
    public void testDetectObjectsWhenCameraDown() {
        // Set camera to DOWN status
        camera.setStatus(Camera.status.DOWN);

        // Should return null when camera is down, regardless of time
        StampedDetectedObjects result = camera.detectObjectsAtTime(1);
        assertNull(result, "Camera should not detect objects when status is DOWN");
    }

    @Test
    public void testMultipleObjectsAtSameTime() {
        // Add another object at time 1
        StampedDetectedObjects sdo = camera.getDetectedObjects().get(0);
        sdo.addDetectedObject(new DetectedObject("obj2", "Test Object 2"));

        // Test detecting both objects
        StampedDetectedObjects result = camera.detectObjectsAtTime(1);
        assertNotNull(result, "Should return objects for time 1");
        assertEquals(2, result.getDetectedObjects().size(), "Should have 2 detected objects");
        assertEquals("obj1", result.getDetectedObjects().get(0).getId(), "First object ID should be 'obj1'");
        assertEquals("obj2", result.getDetectedObjects().get(1).getId(), "Second object ID should be 'obj2'");
    }

    @Test
    public void testMultipleTimestamps() {
        // Add another timestamp
        StampedDetectedObjects sdo2 = new StampedDetectedObjects(2);
        sdo2.addDetectedObject(new DetectedObject("obj3", "Test Object 3"));
        camera.getDetectedObjects().add(sdo2);

        // Test detecting objects at time 2
        StampedDetectedObjects result = camera.detectObjectsAtTime(2);
        assertNotNull(result, "Should return objects for time 2");
        assertEquals(1, result.getDetectedObjects().size(), "Should have 1 detected object");
        assertEquals("obj3", result.getDetectedObjects().get(0).getId(), "Object ID should be 'obj3'");

        // Make sure time 1 still works
        StampedDetectedObjects result1 = camera.detectObjectsAtTime(1);
        assertNotNull(result1, "Should still return objects for time 1");
        assertEquals(1, result1.getDetectedObjects().size(), "Should have 1 detected object for time 1");
    }
}











