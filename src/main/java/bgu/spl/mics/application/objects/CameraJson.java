package bgu.spl.mics.application.objects;
import com.google.gson.annotations.SerializedName;

public class CameraJson {
    private int id;
    private int frequency;

    @SerializedName("camera_key")
    private String cameraKey;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public String getCameraKey() {
        return cameraKey;
    }

    public void setCameraKey(String cameraKey) {
        this.cameraKey = cameraKey;
    }
}