package bgu.spl.mics.application.objects;
import com.google.gson.annotations.SerializedName;
import java.util.Arrays;

public class JSONinput {

    private CamerasConfigurations Cameras;
    private LidarConfigurations LiDarWorkers;
    private String poseJsonFile;
    private int TickTime;
    private int Duration;


    // Default constructor for Gson
    public JSONinput() {}

    // Getters
    public CamerasConfigurations getCameras() {
        return Cameras;
    }

    public LidarConfigurations getLiDarWorkers() {
        return LiDarWorkers;
    }

    public String getPoseJsonFile() {
        return poseJsonFile;
    }

    public int getTickTime() {
        return TickTime;
    }

    public int getDuration() {
        return Duration;
    }







}
