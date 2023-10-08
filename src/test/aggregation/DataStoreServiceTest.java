package test.aggregation;

import main.common.WeatherData;
import main.aggregation.DataStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

public class DataStoreServiceTest {
    private DataStoreService dataStoreService;
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void suppressOutput() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // Discard all data
            }
        }));
    }

    @AfterEach
    public void restoreOutput() {
        System.setOut(originalOut);
    }

    @BeforeEach
    public void setUp() {
        dataStoreService = DataStoreService.getInstance();
    }

    @AfterEach
    public void tearDown() {
        // Here we could do some cleanup if needed.
        dataStoreService.shutdown();
    }

    @Test
    public void testSingletonInstance() {
        DataStoreService anotherInstance = DataStoreService.getInstance();
        assertSame(dataStoreService, anotherInstance, "Both instances should point to the same object");
    }

    @Test
    public void testDataOperations() {
        String key = "TestStation1";
        WeatherData weatherData = new WeatherData(null, 1, "1"); // Assuming WeatherData has an empty constructor, or use another way to instantiate

        dataStoreService.putData(key, weatherData);
        PriorityQueue<WeatherData> data = dataStoreService.getData(key);

        assertNotNull(data);
        assertFalse(data.isEmpty());
        assertEquals(weatherData, data.peek());
    }

    @Test
    public void testTimestampOperations() {
        String key = "TestSender2";
        long timestamp = System.currentTimeMillis();

        dataStoreService.putTimestamp(key, timestamp);
        Long fetchedTimestamp = dataStoreService.getTimestamp(key);

        assertNotNull(fetchedTimestamp);
        assertEquals(timestamp, fetchedTimestamp);
    }

    @Test
    public void testRemoveOperations() {
        String dataKey = "TestDataKey3";
        String timestampKey = "TestTimestampKey3";

        WeatherData weatherData = new WeatherData(null, 1, "TestSender3"); // Instantiate as necessary
        long timestamp = System.currentTimeMillis();

        dataStoreService.putData(dataKey, weatherData);
        dataStoreService.putTimestamp(timestampKey, timestamp);

        dataStoreService.removeDataKey(dataKey);
        dataStoreService.removeTimestampKey(timestampKey);

        assertNull(dataStoreService.getData(dataKey));
        assertNull(dataStoreService.getTimestamp(timestampKey));
    }

    @Test
    public void testRetrieveDataByValidStationId() {
        // Setup
        String stationId = "TestStation4";
        WeatherData sampleData = new WeatherData(null, 1, "TestSender4"); // Assuming WeatherData has an appropriate constructor

        // Add data to the store
        dataStoreService.putData(stationId, sampleData);

        // Retrieve data by station ID
        PriorityQueue<WeatherData> retrievedData = dataStoreService.getData(stationId);

        // Assertions
        assertNotNull(retrievedData, "Retrieved data should not be null");
        assertFalse(retrievedData.isEmpty(), "Retrieved data should not be empty");
        assertEquals(sampleData, retrievedData.peek(), "Retrieved data should match the sample data");
    }

    @Test
    public void testCleanupStaleData() {
        // Setup
        String stationId = "TestStation5";
        String senderID = "TestSender5";
        WeatherData sampleData = new WeatherData(null, 1, senderID); // Assuming WeatherData has an appropriate constructor

        // Add data to the store
        dataStoreService.putData(stationId, sampleData);
        dataStoreService.putTimestamp(senderID, System.currentTimeMillis());

        // Simulate time passing (greater than 30 seconds)
        try {
            Thread.sleep(42000); // Sleep for 42 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Trigger the cleanup mechanism
        dataStoreService.cleanupData(); // Assuming this is how you clean up stale data

        // Try to retrieve the data again
        PriorityQueue<WeatherData> retrievedData = dataStoreService.getData(stationId);

        // Assertions
        assertTrue(retrievedData == null || retrievedData.isEmpty(), "Data older than 30 seconds should be cleaned up and not retrievable");
    }
}

