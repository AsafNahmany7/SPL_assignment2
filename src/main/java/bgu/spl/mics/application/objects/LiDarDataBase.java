package bgu.spl.mics.application.objects;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
/**
 * LiDarDataBase is a singleton class responsible for managing LiDAR data.
 * It provides access to cloud point data and other relevant information for tracked objects.
 */
public class LiDarDataBase {

    private List<StampedCloudPoints> stampedCloud;

    private static class LiDarDataBaseHolder {
        private static final LiDarDataBase INSTANCE = new LiDarDataBase();
    }

    public List<StampedCloudPoints> getStampedCloud() {
        return stampedCloud;
    }

    public StampedCloudPoints getStampedCloudByTime(int time) {
        for (StampedCloudPoints stampedCloudPoints : stampedCloud) {
            if (stampedCloudPoints.getTime() == time)
                return stampedCloudPoints;

        }
        return null;
    }

    private LiDarDataBase(){
        this.stampedCloud = new ArrayList<>();
    }

    /**
     * Returns the singleton instance of LiDarDataBase.
     *
     * @param filePath The path to the LiDAR data file.
     * @return The singleton instance of LiDarDataBase.
     */
    public static LiDarDataBase getInstance(String filePath) {
        LiDarDataBase LDB = LiDarDataBaseHolder.INSTANCE;
        LDB.stampedCloud = parseLidarData(filePath);
        if (LDB.stampedCloud == null) {
            throw new RuntimeException("Failed to parse LiDAR data from file: " + filePath);
        }
        return LDB;
    }

    public static LiDarDataBase getInstance() {
        return LiDarDataBaseHolder.INSTANCE;
    }


    public static List<StampedCloudPoints> parseLidarData (String filePath) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            Type stampedListType = new TypeToken<List<StampedCloudPoints>>() {}.getType();
            List<StampedCloudPoints> stampedList = gson.fromJson(reader, stampedListType);
            return stampedList;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        catch (Exception e) {
            System.err.println("Unexpected Error: " + e.getMessage());
            return null;
        }
    }
}
