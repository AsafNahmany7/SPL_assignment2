package bgu.spl.mics.application.objects;

import java.util.ArrayList;
import java.util.List;

public class StampedTrackedObjects {

        private final int time;
        private List<TrackedObject> TrackedObjects;

        public StampedTrackedObjects(int time) {
            this.time = time;
            TrackedObjects = new ArrayList<>();
        }

        public List<TrackedObject> getTrackedObjectsObjects() {

            return TrackedObjects;
        }

        public int getTime() {

            return time;
        }

        public void addTrackedObject(TrackedObject TO) { //אם משתמש בזה מתישהו אז לשים synchronized
            TrackedObjects.add(TO);
        }
    }




