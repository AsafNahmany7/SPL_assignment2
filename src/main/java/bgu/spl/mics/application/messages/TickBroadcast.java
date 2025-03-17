package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;

/**
 * Represents a broadcast message sent by the TimeService
 * to synchronize time across all services.
 */
public class TickBroadcast implements Broadcast {
    private final int currentTick;


    public TickBroadcast(int currentTick, int duration) {
        this.currentTick = currentTick;
    }

    public int getCurrentTick() {
        return currentTick;
    }



    @Override
    public String toString() {
        return "TickBroadcast{" +
                "currentTick=" + currentTick +
                "}";
    }
    }

