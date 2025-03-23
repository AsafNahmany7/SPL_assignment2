package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.TerminatedBroadcast;
import bgu.spl.mics.application.messages.TickBroadcast;
import bgu.spl.mics.application.objects.StatisticalFolder;
import java.util.concurrent.CountDownLatch;

/**
 * TimeService acts as the global timer for the system, broadcasting TickBroadcast messages
 * at regular intervals and controlling the simulation's duration.
 */
public class TimeService extends MicroService {
    private final int tickTime;
    private final int duration;
    private int currentTick;
    private final CountDownLatch latch;
    private Thread timerThread;


    /**
     * Constructor for TimeService.
     *
     * @param tickTime  The duration of each tick in milliseconds.
     * @param duration  The total number of ticks before the service terminates.
     */
    public TimeService(int tickTime, int duration, CountDownLatch latch) {
        super("TimeService");
        this.tickTime = tickTime;
        this.duration = duration;
        this.currentTick = 0;
        this.latch = latch;
    }

    /**
     * Initializes the TimeService.
     * Starts broadcasting TickBroadcast messages and terminates after the specified duration.
     */
    @Override
    protected void initialize() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Thread to simulate ticking
        timerThread = new Thread(() -> {
            try {
                StatisticalFolder statsFolder = StatisticalFolder.getInstance();
                System.out.println("בדיקה -t.s." + currentTick);
                while (currentTick < duration && !timerThread.isInterrupted()) {
                    currentTick++;
                    this.time=currentTick;
                    System.out.println("⏰sending tick: " + currentTick);
                    sendBroadcast(new TickBroadcast(currentTick, duration));
                    statsFolder.incrementSystemRuntime();
                    Thread.sleep(tickTime);
                }
                // Final broadcast
                sendBroadcast(new TickBroadcast(currentTick, duration)); // Last tick
                System.out.println("finished all the ticks, sending termination broadcast");
                sendBroadcast(new TerminatedBroadcast("TimeService", TimeService.class));
                System.out.println("sent terminate broadcast from timeservice");

                // DON'T terminate here - wait for FusionSlamService's broadcast instead
            } catch (InterruptedException e) {
                System.out.println("timeservice catch - interrupted");
                Thread.currentThread().interrupt();
            }
        });
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(getName() + " received terminated broadcast.");
            if(terminated.getServiceClass()!= null && terminated.getServiceClass().equals(FusionSlamService.class)){
                System.out.println("sending interrupt to time service at tick " + currentTick);
                timerThread.interrupt();
                terminate();
            }

        });
        // Start the timer thread
        timerThread.start();
        System.out.println("timerser End initialized ]]]]]]]]]]");
        System.out.println("בדיקהt.s. - tick:"+ currentTick);
    }
}
