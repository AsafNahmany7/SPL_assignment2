package bgu.spl.mics.application.objects;

import java.util.concurrent.atomic.AtomicInteger;

public class DetectStat {


    private AtomicInteger time;
    private AtomicInteger numOfDetections;

    public DetectStat(int time, int numOfDetections) {
        this.time = new AtomicInteger(time);
        this.numOfDetections = new AtomicInteger(numOfDetections);
    }

    public int getTime() {
        return time.get();
    }


    public int getNumOfDetections() {
        return numOfDetections.get();
    }


}
