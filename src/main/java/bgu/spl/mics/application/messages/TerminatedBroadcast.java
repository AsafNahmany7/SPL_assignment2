package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;
import bgu.spl.mics.MicroService;

/**
 * Represents a broadcast message sent by sensors or services
 * to notify the system that the sending service is terminating.
 */
public class TerminatedBroadcast implements Broadcast {
    private final String serviceName;
    private final Class<?> serviceClass;
    private final MicroService microService;
    public TerminatedBroadcast(String serviceName, Class<?> serviceClass, MicroService microService) {
        this.serviceName = serviceName;
        this.serviceClass = serviceClass;
        this.microService = microService;
    }

    public MicroService getMicroService() {
            return microService;
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
