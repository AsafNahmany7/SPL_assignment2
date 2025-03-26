package bgu.spl.mics.application.objects;

import java.util.concurrent.atomic.AtomicInteger;

public class TrackStat {
    private AtomicInteger time;
    AtomicInteger NumOfTracks;

    public TrackStat(int time, int NumOfTracks) {
        this.time = new AtomicInteger(time);
        this.NumOfTracks = new AtomicInteger(NumOfTracks); // אתחול ל-0
    }
    public int getTime() {
        return time.get();
    }

    public int getNumOfTracks() {
        return NumOfTracks.get();
    }


}
