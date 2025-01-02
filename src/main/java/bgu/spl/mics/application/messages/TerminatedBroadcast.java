package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;

/**
 * Represents a broadcast message sent by sensors or services
 * to notify the system that the sending service is terminating.
 */
public class TerminatedBroadcast implements Broadcast {
    private final String serviceName;

    public TerminatedBroadcast(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String toString() {
        return "TerminatedBroadcast{" +
                "serviceName='" + serviceName + '\'' +
                '}';
    }
}
