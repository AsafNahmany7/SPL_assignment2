package bgu.spl.mics.application.objects;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
/**
 * LiDarDataBase is a singleton class responsible for managing LiDAR data.
 * It provides access to cloud point data and other relevant information for tracked objects.
 */
public class LiDarDataBase {

    private List<StampedCloudPoints> stampedCloud;
    private static LiDarDataBase instance;

    public List<StampedCloudPoints> getStampedCloud() {
        return stampedCloud;
    }

    private LiDarDataBase() {
    }
    /**
     * Returns the singleton instance of LiDarDataBase.
     *
     * @param filePath The path to the LiDAR data file.
     * @return The singleton instance of LiDarDataBase.
     */
    public static LiDarDataBase getInstance(String filePath) {
        if (instance == null) {
            synchronized (LiDarDataBase.class) {
                if (instance == null) {
                    try {
                        instance = new LiDarDataBase();
                        instance.stampedCloud = parseLidarData(filePath);
                        if (instance.stampedCloud == null) {
                            throw new RuntimeException("Failed to parse LiDAR data from file: " + filePath);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Unexpected error initializing LiDarDataBase", e);
                    }
                }
            }
        }
        return instance;
    }

    public static List<StampedCloudPoints> parseLidarData(String filePath) {
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
