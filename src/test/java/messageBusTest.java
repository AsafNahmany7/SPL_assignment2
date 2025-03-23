import bgu.spl.mics.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class messageBusTest {
    private MessageBusImpl messageBus;
    private MicroService testService1;
    private MicroService testService2;

    @BeforeEach
    public void setUp() {
        MessageBusImpl.reset(); // Reset the singleton before each test
        messageBus = (MessageBusImpl) MessageBusImpl.getInstance();
        // Create test microservices
        testService1 = new DummyMicroService("service1");
        testService2 = new DummyMicroService("service2");

        // Register microservices
        messageBus.register(testService1);
        messageBus.register(testService2);
    }
    @AfterEach
    public void tearDown() {
        // Unregister test services
        messageBus.unregister(testService1);
        messageBus.unregister(testService2);
        // Any other cleanup needed
    }

    @Test
    public void testSubscribeEvent() {
        // Create test event type
        TestEvent event = new TestEvent("test");
        messageBus.subscribeEvent(TestEvent.class, testService1);

        // Send event
        Future<String> future = messageBus.sendEvent(event);

        // Check if event was sent to the right service
        try {
            Message receivedMessage = messageBus.awaitMessage(testService1);
            assertTrue(receivedMessage instanceof TestEvent);
            assertEquals(event, receivedMessage);
        } catch (InterruptedException e) {
            fail("InterruptedException occurred: " + e.getMessage());
        }

        assertNotNull(future);
    }

    @Test
    public void testSubscribeBroadcast() {
        // Create test broadcast
        TestBroadcast broadcast = new TestBroadcast("test broadcast");
        messageBus.subscribeBroadcast(TestBroadcast.class, testService1);
        messageBus.subscribeBroadcast(TestBroadcast.class, testService2);

        // Send broadcast
        messageBus.sendBroadcast(broadcast);

        // Check if broadcast was received by all subscribers
        try {
            Message message1 = messageBus.awaitMessage(testService1);
            Message message2 = messageBus.awaitMessage(testService2);

            assertTrue(message1 instanceof TestBroadcast);
            assertTrue(message2 instanceof TestBroadcast);
            assertEquals(broadcast, message1);
            assertEquals(broadcast, message2);
        } catch (InterruptedException e) {
            fail("InterruptedException occurred: " + e.getMessage());
        }
    }

    @Test
    public void testComplete() throws InterruptedException {
        TestEvent event = new TestEvent("test");
        messageBus.subscribeEvent(TestEvent.class, testService1);

        Future<String> future = messageBus.sendEvent(event);

        // Complete the event
        messageBus.complete(event, "result");

        // Check if the future was resolved with a timeout
        assertTrue(future.isDone());
        assertEquals("result", future.get(100, TimeUnit.MILLISECONDS)); // Add timeout
    }

    @Test
    public void testUnregister() throws InterruptedException {
        // Register and subscribe
        messageBus.register(testService1);
        messageBus.subscribeEvent(TestEvent.class, testService1);

        // Send an event
        TestEvent event1 = new TestEvent("test1");
        messageBus.sendEvent(event1);

        // Verify we can receive it
        Message message = messageBus.awaitMessage(testService1);
        assertNotNull(message);

        // Unregister
        messageBus.unregister(testService1);

        // Now check that the service is truly unregistered
        // This should throw IllegalStateException
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            messageBus.awaitMessage(testService1);
        });

        // Check the exception message if needed
        // assertTrue(exception.getMessage().contains("never registered"));
    }

    @Test
    public void testRoundRobin() {
        // Test that events are distributed in round-robin fashion
        TestEvent event1 = new TestEvent("test1");
        TestEvent event2 = new TestEvent("test2");

        MicroService service3 = new DummyMicroService("service3");
        messageBus.register(service3);

        messageBus.subscribeEvent(TestEvent.class, testService1);
        messageBus.subscribeEvent(TestEvent.class, testService2);
        messageBus.subscribeEvent(TestEvent.class, service3);

        // Send events
        messageBus.sendEvent(event1); // Should go to service1
        messageBus.sendEvent(event2); // Should go to service2
        TestEvent event3 = new TestEvent("test3"); // Should go to service3
        messageBus.sendEvent(event3);

        try {
            Message message1 = messageBus.awaitMessage(testService1);
            Message message2 = messageBus.awaitMessage(testService2);
            Message message3 = messageBus.awaitMessage(service3);

            assertEquals(event1, message1);
            assertEquals(event2, message2);
            assertEquals(event3, message3);
        } catch (InterruptedException e) {
            fail("InterruptedException occurred: " + e.getMessage());
        }
    }

    // Helper classes for testing
    private class DummyMicroService extends MicroService {
        public DummyMicroService(String name) {
            super(name);
        }

        @Override
        protected void initialize() {
            // No initialization needed for test
        }
    }

    private class TestEvent implements Event<String> {
        private final String content;

        public TestEvent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    private class TestBroadcast implements Broadcast {
        private final String content;

        public TestBroadcast(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}