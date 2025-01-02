package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.TickBroadcast;

/**
 * TimeService acts as the global timer for the system, broadcasting TickBroadcast messages
 * at regular intervals and controlling the simulation's duration.
 */
public class TimeService extends MicroService {
    private final int tickTime;
    private final int duration;
    private int currentTick;

    /**
     * Constructor for TimeService.
     *
     * @param tickTime  The duration of each tick in milliseconds.
     * @param duration  The total number of ticks before the service terminates.
     */
    public TimeService(int tickTime, int duration) {
        super("TimeService");
        this.tickTime = tickTime;
        this.duration = duration;
        this.currentTick = 0;
    }

    /**
     * Initializes the TimeService.
     * Starts broadcasting TickBroadcast messages and terminates after the specified duration.
     */
    @Override
    protected void initialize() {
        // Thread to simulate ticking
        Thread timerThread = new Thread(() -> {
            try {
                while (currentTick < duration) {
                    currentTick++;
                    sendBroadcast(new TickBroadcast(currentTick, duration));
                    Thread.sleep(tickTime);
                }
                // Final broadcast and terminate
                sendBroadcast(new TickBroadcast(currentTick, duration)); // Last tick
                terminate();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Start the timer thread
        timerThread.start();
    }
}
