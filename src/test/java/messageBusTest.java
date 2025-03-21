import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import bgu.spl.mics.*;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the MessageBusImpl class
 */
public class messageBusTest {

    private MessageBus messageBus;

    // Test implementations of Message interfaces
    class TestEvent implements Event<String> {}
    class TestBroadcast implements Broadcast {}

    // Simple MicroService for testing
    class TestMicroService extends MicroService {
        public TestMicroService(String name) {
            super(name);
        }

        @Override
        protected void initialize() {
            // Empty implementation for testing
        }
    }

    @BeforeEach
    public void setUp() {
        messageBus = MessageBusImpl.getInstance();
    }

    @Test
    public void testRegisterAndUnregister() {
        // Create a test service
        MicroService m = new TestMicroService("testService");

        // Register the service
        messageBus.register(m);

        // Unregister the service
        messageBus.unregister(m);

        // Try to await message - should throw exception
        assertThrows(IllegalStateException.class, () -> {
            messageBus.awaitMessage(m);
        });
    }

    @Test
    public void testSubscribeBroadcastAndSendBroadcast() throws InterruptedException {
        // Create test service and broadcast
        MicroService m = new TestMicroService("broadcastService");
        TestBroadcast broadcast = new TestBroadcast();

        // Register and subscribe
        messageBus.register(m);
        messageBus.subscribeBroadcast(TestBroadcast.class, m);

        // Send broadcast
        messageBus.sendBroadcast(broadcast);

        // Service should receive the broadcast
        Message received = messageBus.awaitMessage(m);
        assertTrue(received instanceof TestBroadcast);

        // Clean up
        messageBus.unregister(m);
    }

    @Test
    public void testSubscribeEventAndSendEvent() throws InterruptedException {
        // Create test service and event
        MicroService m = new TestMicroService("eventService");
        TestEvent event = new TestEvent();

        // Register and subscribe
        messageBus.register(m);
        messageBus.subscribeEvent(TestEvent.class, m);

        // Send event
        Future<String> future = messageBus.sendEvent(event);
        assertNotNull(future);

        // Service should receive the event
        Message received = messageBus.awaitMessage(m);
        assertTrue(received instanceof TestEvent);

        // Complete the event
        messageBus.complete(event, "testResult");

        // Future should be resolved
        assertTrue(future.isDone());
        assertEquals("testResult", future.get());

        // Clean up
        messageBus.unregister(m);
    }

    @Test
    public void testSendEventWithNoSubscribers() {
        // Send an event with no subscribers
        TestEvent event = new TestEvent();
        Future<String> future = messageBus.sendEvent(event);

        // Future should be null since no one is subscribed
        assertNull(future);
    }

    @Test
    public void testRoundRobinEventDistribution() throws InterruptedException {
        // Create test services and events
        MicroService m1 = new TestMicroService("service1");
        MicroService m2 = new TestMicroService("service2");
        TestEvent event1 = new TestEvent();
        TestEvent event2 = new TestEvent();

        // Register and subscribe both services
        messageBus.register(m1);
        messageBus.register(m2);
        messageBus.subscribeEvent(TestEvent.class, m1);
        messageBus.subscribeEvent(TestEvent.class, m2);

        // Send two events
        messageBus.sendEvent(event1);
        messageBus.sendEvent(event2);

        // Each service should get one event in round-robin fashion
        Message received1 = messageBus.awaitMessage(m1);
        Message received2 = messageBus.awaitMessage(m2);

        assertTrue(received1 instanceof TestEvent);
        assertTrue(received2 instanceof TestEvent);

        // Clean up
        messageBus.unregister(m1);
        messageBus.unregister(m2);
    }

    @Test
    public void testUnregisterRemovesSubscriptions() throws InterruptedException {
        // Create test services and messages
        MicroService m = new TestMicroService("unregisterTest");
        TestEvent event = new TestEvent();
        TestBroadcast broadcast = new TestBroadcast();

        // Register and subscribe
        messageBus.register(m);
        messageBus.subscribeEvent(TestEvent.class, m);
        messageBus.subscribeBroadcast(TestBroadcast.class, m);

        // Unregister the service
        messageBus.unregister(m);

        // Send messages - nothing should break, but futures should be null
        Future<String> future = messageBus.sendEvent(event);
        messageBus.sendBroadcast(broadcast);

        assertNull(future);
    }

    @Test
    public void testAwaitMessageTimeout() {
        // Create test service
        MicroService m = new TestMicroService("timeoutTest");

        // Register the service
        messageBus.register(m);

        // Try to await message with timeout - should return null after timeout
        Thread t = new Thread(() -> {
            try {
                // This should timeout and return null
                Future<String> future = new Future<>();
                String result = future.get(100, TimeUnit.MILLISECONDS);
                assertNull(result);
            } catch (Exception e) {
                // More general exception handling
                fail("Unexpected exception: " + e.getMessage());
            }
        });

        t.start();
        try {
            t.join(200); // Give thread time to complete
        } catch (InterruptedException e) {
            fail("Thread joining was interrupted");
        }
        assertFalse(t.isAlive()); // Thread should have completed

        // Clean up
        messageBus.unregister(m);
    }

    @Test
    public void testCompleteNonExistentEvent() {
        // Try to complete an event that was never sent
        TestEvent event = new TestEvent();

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            messageBus.complete(event, "result");
        });
    }
}
