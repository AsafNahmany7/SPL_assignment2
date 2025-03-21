package bgu.spl.mics.application.objects;

import com.google.gson.annotations.SerializedName;

public class LidarConfigurations {
    @SerializedName("LidarConfigurations")
    private LiDarJson[] lidarWorkers;

    private String lidars_data_path;

    public LiDarJson[] getLiDarWorkers() {
        return lidarWorkers;
    }

    public void setLiDarWorkers(LiDarJson[] lidarWorkers) {
        this.lidarWorkers = lidarWorkers;
    }

    public String getLidars_data_path() {
        return lidars_data_path;
    }

    public void setLidars_data_path(String lidars_data_path) {
        this.lidars_data_path = lidars_data_path;
    }
}
