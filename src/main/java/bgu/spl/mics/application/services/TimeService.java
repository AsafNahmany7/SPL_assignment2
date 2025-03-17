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
        System.out.println("timeser initialize and should wait to latch");
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("timeser finish to wait for latch and should start sending ticks");
        // Thread to simulate ticking
        timerThread = new Thread(() -> {
            try {
                StatisticalFolder statsFolder = StatisticalFolder.getInstance();
                while (currentTick < duration) {
                    currentTick++;
                    sendBroadcast(new TickBroadcast(currentTick, duration));
                    System.out.println("**sended tick" + currentTick);
                    statsFolder.incrementSystemRuntime();
                    Thread.sleep(tickTime);
                }
                // Final broadcast and terminate
                sendBroadcast(new TickBroadcast(currentTick, duration)); // Last tick
                System.out.println("finished all the ticks,and suposed to terminate");
                sendBroadcast(new TerminatedBroadcast("TimeService"));
                System.out.println("sended terminate broadcats of timeservice");
                terminate();
                System.out.println("timeservice terminated-------");
                if(timerThread != null){
                    System.out.println("timeservice interupting <<<<<<{{{{");
                    timerThread.interrupt();
                    System.out.println("timeservice interupting >>>>>>>>}}}}}");
                }
            } catch (InterruptedException e) {
                System.out.println("timeservice catch------------");
                Thread.currentThread().interrupt();
                timerThread.interrupt();
            } finally {
                System.out.println("timeservice finally-----------");
                this.terminate(); // ווידוא קריאה ל-terminate גם במקרה של חריגה
                if(timerThread != null){
                    System.out.println("timeservice interupting <<<<<<{{{{");
                    timerThread.interrupt();
                    System.out.println("timeservice interupting >>>>>>>>}}}}}");
                }
            }
        });
        subscribeBroadcast(TerminatedBroadcast.class, terminated -> {
            System.out.println(getName() + " received terminated broadcast.");
            terminate();
        });
        // Start the timer thread
        timerThread.start();
        System.out.println("timerser End initialized ]]]]]]]]]]");
    }
}
