package test.aggregation;

import main.common.WeatherData;
import main.aggregation.DataStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

public class DataStoreServiceTest {

    private DataStoreService dataStoreService;

    @BeforeEach
    public void setUp() {
        dataStoreService = DataStoreService.getInstance();
    }

    @AfterEach
    public void tearDown() {
        // Here we could do some cleanup if needed.
    }

    @Test
    public void testSingletonInstance() {
        DataStoreService anotherInstance = DataStoreService.getInstance();
        assertSame(dataStoreService, anotherInstance, "Both instances should point to the same object");
    }

    @Test
    public void testDataOperations() {
        String key = "TestStation";
        WeatherData weatherData = new WeatherData(null, 1, "TestSender"); // Assuming WeatherData has an empty constructor, or use another way to instantiate

        dataStoreService.putData(key, weatherData);
        PriorityQueue<WeatherData> data = dataStoreService.getData(key);

        assertNotNull(data);
        assertFalse(data.isEmpty());
        assertEquals(weatherData, data.peek());
    }

    @Test
    public void testTimestampOperations() {
        String key = "TestSender";
        long timestamp = System.currentTimeMillis();

        dataStoreService.putTimestamp(key, timestamp);
        Long fetchedTimestamp = dataStoreService.getTimestamp(key);

        assertNotNull(fetchedTimestamp);
        assertEquals(timestamp, fetchedTimestamp);
    }

    @Test
    public void testRemoveOperations() {
        String dataKey = "TestDataKey";
        String timestampKey = "TestTimestampKey";

        WeatherData weatherData = new WeatherData(null, 1, "TestSender"); // Instantiate as necessary
        long timestamp = System.currentTimeMillis();

        dataStoreService.putData(dataKey, weatherData);
        dataStoreService.putTimestamp(timestampKey, timestamp);

        dataStoreService.removeDataKey(dataKey);
        dataStoreService.removeTimestampKey(timestampKey);

        assertNull(dataStoreService.getData(dataKey));
        assertNull(dataStoreService.getTimestamp(timestampKey));
    }

    @Test
    public void testCleanup() {
        // This test is more complex because it depends on internal logic and timings.
        // For a more comprehensive test, you might want to mock System.currentTimeMillis() or refactor the cleanup method to be more testable.
        // For now, we'll just invoke it and see if it runs without errors.
        dataStoreService.cleanupData();
    }

    @Test
    public void testShutdown() {
        // Test the shutdown method. This will terminate the scheduler so be careful when adding more tests after this.
        // Ideally, this should be the last test.
        dataStoreService.shutdown();
    }

    // ... Add more tests as needed
}

