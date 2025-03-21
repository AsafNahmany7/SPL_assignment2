package bgu.spl.mics.application.objects;

import com.google.gson.annotations.SerializedName;

public class CamerasConfigurations {
    @SerializedName("CamerasConfigurations")
    private CameraJson[] cameras;

    @SerializedName("camera_datas_path")
    private String cameraDatasPath;

    public CameraJson[] getCamerasConfigurations() {
        return cameras;
    }

    public void setCamerasConfigurations(CameraJson[] cameras) {
        this.cameras = cameras;
    }

    public String getCameraDatasPath() {
        return cameraDatasPath;
    }

    public void setCameraDatasPath(String cameraDatasPath) {
        this.cameraDatasPath = cameraDatasPath;
    }
}
