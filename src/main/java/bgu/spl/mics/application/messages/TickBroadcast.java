package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;

/**
 * Represents a broadcast message sent by the TimeService
 * to synchronize time across all services.
 */
public class TickBroadcast implements Broadcast {
    private final int currentTick;
    private final int duration;

    public TickBroadcast(int currentTick, int duration) {
        this.currentTick = currentTick;
        this.duration = duration;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "TickBroadcast{" +
                "currentTick=" + currentTick +
                ", duration=" + duration +
                '}';
    }
}
