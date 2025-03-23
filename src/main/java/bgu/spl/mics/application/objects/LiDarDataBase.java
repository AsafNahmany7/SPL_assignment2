package bgu.spl.mics.application.objects;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
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

    public StampedCloudPoints searchStampedClouds(int time,String id) {
        //Assume correct input
        StampedCloudPoints output = null;
        //System.out.println("\uD83C\uDFB2 מתחיל לחפש");
        for (StampedCloudPoints stampedCloudPoints : stampedCloud) {
            //System.out.println(stampedCloudPoints.getTime() + "--->" + time);
            //System.out.println(stampedCloudPoints.getId() + "--->" + id);
            if (stampedCloudPoints.getTime() == time) {
                if (stampedCloudPoints.getId().equals(id)) {
                    //System.out.println("מצאתי ! \uD83D\uDECE\uFE0F");
                    output = stampedCloudPoints;
                    break;
                }
            }
        }
        return output;
    }

    private LiDarDataBase() {
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


    public static List<StampedCloudPoints> parseLidarData(String filePath) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<StampedCloudPoints>>() {
            }.getType();
            List<StampedCloudPoints> rawData = gson.fromJson(reader, listType);

            // ✅ Debugging: Print parsed JSON structure
            System.out.println("🔹 Raw JSON Data Parsed:");
            System.out.println(rawData);

            List<StampedCloudPoints> processedData = new ArrayList<>();

            for (StampedCloudPoints rawPoint : rawData) {
                // ✅ Debugging: Check each `StampedCloudPoints` object
                System.out.println("\n🔹 Processing StampedCloudPoints -> ID: " + rawPoint.getId() + ", Time: " + rawPoint.getTime());

                // ✅ Step 1: Create StampedCloudPoints object from time and id
                StampedCloudPoints a = new StampedCloudPoints(rawPoint.getId(), rawPoint.getTime());

                // ✅ Debugging: Print `cloudPoints` raw structure
                System.out.println("  📌 Raw CloudPoints Data: " + rawPoint.getCloudPoints());

                // ✅ Step 2: Extract only x and y from cloudPoints
                for (Object obj : rawPoint.getCloudPoints()) {
                    if (!(obj instanceof List<?>)) {
                        System.err.println("  ❌ ERROR: Expected List<Double> but found -> " + obj.getClass().getSimpleName());
                        continue; // Skip invalid entries
                    }
                    List<?> point = (List<?>) obj;

                    // Ensure at least x and y exist
                    if (point.size() >= 2 && point.get(0) instanceof Double && point.get(1) instanceof Double) {
                        double x = (Double) point.get(0);
                        double y = (Double) point.get(1);

                        // ✅ Debugging: Print each extracted cloud point
                        System.out.println("  ✅ Extracted CloudPoint -> X: " + x + ", Y: " + y);

                        a.addCloudPoint(x, y);
                    } else {
                        System.err.println("  ❌ ERROR: Invalid cloud point format -> " + point);
                    }
                }

                // ✅ Step 3: Add processed StampedCloudPoints to the list
                processedData.add(a);
            }

            // ✅ Debugging: Print the final list of processed objects
            System.out.println("\n✅ Successfully processed LiDAR data: " + processedData.size() + " entries.");
            return processedData;

        } catch (IOException e) {
            System.err.println("❌ ERROR: Failed to read the JSON file.");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("❌ ERROR: Unexpected exception while parsing.");
            e.printStackTrace();
            return null;
        }
    }
}
