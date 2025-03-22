package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;

/**
 * Represents a broadcast message sent by sensors or services
 * to notify the system that the sending service is terminating.
 */
public class TerminatedBroadcast implements Broadcast {
    private final String serviceName;
    private final Class<?> serviceClass;

    public TerminatedBroadcast(String serviceName, Class<?> serviceClass) {
        this.serviceName = serviceName;
        this.serviceClass = serviceClass;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }
    @Override
    public String toString() {
        return "TerminatedBroadcast{" +
                "serviceName='" + serviceName + '\'' +
                '}';
    }
}
