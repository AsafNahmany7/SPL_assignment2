package bgu.spl.mics.application.messages;

import bgu.spl.mics.Broadcast;
import bgu.spl.mics.MicroService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a broadcast message sent by a sensor or service
 * to notify the system that it has crashed.
 */
public class CrashedBroadcast implements Broadcast {
    private final String serviceName;
    private final AtomicInteger crashTime;
    private final Class<?> serviceClass;
    private final MicroService microService;


    public CrashedBroadcast(String serviceName,int crashTime, Class<?> serviceClass, MicroService microService) {
        this.serviceName = serviceName;
        this.crashTime=new AtomicInteger(crashTime);
        this.serviceClass=serviceClass;
        this.microService=microService;

    }

    public MicroService getMicroService() {
        return microService;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }



    public String getServiceName() {
        return serviceName;
    }

    public int getCrashTime(){
        return crashTime.get();
    }

    @Override
    public String toString() {
        return "CrashedBroadcast{" +
                "serviceName='" + serviceName + '\'' +
                '}';
    }
}
