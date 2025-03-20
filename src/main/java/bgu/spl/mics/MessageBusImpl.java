package bgu.spl.mics;

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

		if(!BrodcastSubscribersQueues.get(type).contains(m)) {
			BrodcastSubscribersQueues.get(type).add(m);
		}
		System.out.println( m.getName() + "(M.Bus) subs to " + type);
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
			System.out.println("No Such brodcast is listed please list it with a subscribing microservice first!");
			return;
		}

		if(BrodcastSubscribersQueues.get(b.getClass()).isEmpty()) {
			System.out.println("No microservices subscribed with this event!");
			return;
		}

		BlockingQueue<MicroService> microServicesQueue = BrodcastSubscribersQueues.get(b.getClass());

		for (MicroService m : microServicesQueue) {
			try {
				BlockingQueue<Message> messageQueue = ServicesMessageQueues.get(m);
				if (messageQueue != null) {
					messageQueue.put(b);
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

	}

	
	@Override
	public <T> Future<T> sendEvent(Event<T> e) {
		System.out.println( e.getClass() + "האם sendevent הגיע למסג'-באס ?");
		if(!EventsSubscribersQueues.containsKey(e.getClass())) {
			System.out.println(e.getClass() + "No Such Event is listed please list it with a subscribing microservice first!");
			return null;
		}

		if(EventsSubscribersQueues.get(e.getClass()).isEmpty()) {
			System.out.println("No microservices subscribed with this event!");
			return null;
		}

		MicroService m = EventsSubscribersQueues.get(e.getClass()).poll();
		System.out.println( "לתפעול ה event" + e.getClass() + "נבחר המיקרוסרוויס: " + m.getName());
		try {
			ServicesMessageQueues.get(m).put(e);
			System.out.println( "לתור ההודעות של " + m.getName() + "הוכנס הevent: " + e.getClass());
		} catch (InterruptedException ex) {
			System.out.println( "הפריעו לsendevent");
			Thread.currentThread().interrupt();
		}

		try {
			EventsSubscribersQueues.get(e.getClass()).put(m);
			System.out.println("לתור הevent: " + e.getClass() + "הוכנס חזרה המיקרוסרוויס: " + m.getName());
		} catch (InterruptedException ex) {
			System.out.println( "הפריעו לsendevent");
			Thread.currentThread().interrupt();
		}

		Future<T> output = new Future<>();
		EventsFutures.put(e, output);
		return output;
	}

	@Override
	public void register(MicroService m) {

		ServicesMessageQueues.putIfAbsent( m, new LinkedBlockingQueue<>());
	}

	@Override
	public void unregister(MicroService m) {
		if (!ServicesMessageQueues.containsKey(m))
			return;

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
		return ServicesMessageQueues.get(m).take();
	}

}
