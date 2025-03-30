package bgu.spl.mics;

import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The {@link MessageBusImpl class is the implementation of the MessageBus interface.
 * Write your implementation here!
 * Only one public method (in addition to getters which can be public solely for unit testing) may be added to this class
 * All other methods and members you add the class must be private.
 */
public class MessageBusImpl implements MessageBus {

	private static MessageBusImpl MessageBus = new MessageBusImpl();
	private ConcurrentHashMap<MicroService, BlockingQueue<Message>> ServicesMessageQueues = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Class<? extends Message>,BlockingQueue<MicroService>> BrodcastSubscribersQueues = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Class<? extends Message>,BlockingQueue<MicroService>> EventsSubscribersQueues = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Event<?>,Future<?>> EventsFutures = new ConcurrentHashMap<>();


	private MessageBusImpl() {}

	public static MessageBus getInstance() {
		return MessageBus;
	}
	public static void reset() {
		MessageBus = new MessageBusImpl(); // Create a fresh instance
	}

	@Override
	public <T> void subscribeEvent(Class<? extends Event<T>> type, MicroService m) {
		EventsSubscribersQueues.putIfAbsent(type,new LinkedBlockingQueue<>());

		if (!EventsSubscribersQueues.get(type).contains(m)) {
			EventsSubscribersQueues.get(type).add(m);
		}
	}

	@Override
	public void subscribeBroadcast(Class<? extends Broadcast> type, MicroService m) {
		BrodcastSubscribersQueues.putIfAbsent(type,new LinkedBlockingQueue<>());
		//System.out.println(Thread.currentThread().getName() + "recived broadcat:" + type.getName() + "from: ");

		if(!BrodcastSubscribersQueues.get(type).contains(m)) {
			BrodcastSubscribersQueues.get(type).add(m);
		}
	}

	@Override
	public <T> void complete(Event<T> e, T result) {
		if(!EventsFutures.containsKey(e))
			throw new IllegalStateException("Event not found in EventsFutures: " + e);

		Future<T> eventFu = (Future<T>)EventsFutures.get(e);
		eventFu.resolve(result);
	}

	@Override
	public void sendBroadcast(Broadcast b) {

		if(!BrodcastSubscribersQueues.containsKey(b.getClass())) {
			return;
		}

		if(BrodcastSubscribersQueues.get(b.getClass()).isEmpty()) {
			return;
		}

		BlockingQueue<MicroService> microServicesQueue = BrodcastSubscribersQueues.get(b.getClass());



		for (MicroService m : microServicesQueue) {
			try {
				BlockingQueue<Message> messageQueue = ServicesMessageQueues.get(m);
				if (messageQueue != null) {
					if(b.getClass() == TerminatedBroadcast.class) {
					System.out.println("☕☕☕☕☕☕☕☕☕☕☕☕☕☕TerminatedBroadcast sent by " + m.getName());
					}
					messageQueue.put(b);
					for (Message message : messageQueue) {
					}
				}

			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		if(b.getClass() == TickBroadcast.class){
		}

	}


	@Override
	public <T> Future<T> sendEvent(Event<T> e) {
		if (!EventsSubscribersQueues.containsKey(e.getClass())) {
			return null;
		}

		if (EventsSubscribersQueues.get(e.getClass()).isEmpty()) {
			return null;
		}

		MicroService m = null;
		try {
			// Use take() instead of poll() to block until a microservice is available
			m = EventsSubscribersQueues.get(e.getClass()).take();

			// Add the event to the microservice's queue
			ServicesMessageQueues.get(m).put(e);

			// Put the microservice back in the queue
			EventsSubscribersQueues.get(e.getClass()).put(m);

			// Create and register the future for this event
			Future<T> output = new Future<>();
			EventsFutures.put(e, output);
			return output;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();

			// If we managed to get a microservice before interruption, put it back
			if (m != null) {
				try {
					EventsSubscribersQueues.get(e.getClass()).put(m);
				} catch (InterruptedException putEx) {
					Thread.currentThread().interrupt();
				}
			}
			return null;
		}
	}

	@Override
	public void register(MicroService m) {

		ServicesMessageQueues.putIfAbsent( m, new LinkedBlockingQueue<>());
	}

	@Override
	public void unregister(MicroService m) {
		if (!ServicesMessageQueues.containsKey(m)) {
			return;
		}
		BlockingQueue<Message> queue = ServicesMessageQueues.get(m);
		List<Event<?>> eventsToRemove = new ArrayList<>();

		for (Message message : queue) {
			if (message instanceof Event) {
				Event<?> event = (Event<?>) message;
				eventsToRemove.add(event);
				EventsFutures.get(event).resolve(null);
			}
		}

		for (Event<?> event : eventsToRemove) {
			EventsFutures.remove(event);
		}

		// הסרת המיקרו-שירות מכל התורים של Broadcast
		for (BlockingQueue<MicroService> broadcastQueue : BrodcastSubscribersQueues.values()) {
			broadcastQueue.remove(m);
		}

		// הסרת המיקרו-שירות מכל התורים של Events
		for (BlockingQueue<MicroService> eventQueue : EventsSubscribersQueues.values()) {
			eventQueue.remove(m);
		}

		ServicesMessageQueues.remove(m);

	}

	@Override
	public Message awaitMessage(MicroService m) throws InterruptedException {
		if (!ServicesMessageQueues.containsKey(m)) {
			throw new IllegalStateException("MicroService never registered");
		}

		return ServicesMessageQueues.get(m).take();
	}

}
