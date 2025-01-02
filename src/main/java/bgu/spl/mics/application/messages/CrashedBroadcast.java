package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;

/**
 * Represents a broadcast message sent by a sensor or service
 * to notify the system that it has crashed.
 */
public class CrashedBroadcast implements Broadcast {
    private final String serviceName;

    public CrashedBroadcast(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String toString() {
        return "CrashedBroadcast{" +
                "serviceName='" + serviceName + '\'' +
                '}';
    }
}
