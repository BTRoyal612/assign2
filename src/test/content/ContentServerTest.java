package test.content;

import main.content.ContentServer;
import test.network.StubNetworkHandler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ContentServerTest {
    private ContentServer contentServer;
    private StubNetworkHandler stubNetworkHandler;

    @BeforeEach
    public void setUp() {
        stubNetworkHandler = new StubNetworkHandler();
        contentServer = new ContentServer(stubNetworkHandler); // You'd need to modify the ContentServer's constructor to accept a NetworkHandler
    }

    @Test
    public void testLoadWeatherData() {
        String testDataPath = "src/test/content/input_test.txt"; // Path to some test JSON data
        contentServer.loadWeatherData(testDataPath);
        assertNotNull(contentServer.getWeatherData(), "Weather data should be loaded");
    }

    @Test
    public void testStartUploadWeatherData() {
        // Assuming weather data is loaded first
        contentServer.loadWeatherData("src/test/content/input_test.txt");

        contentServer.uploadWeatherData("testServer", 8080);

        // Give it a little time for the initial data upload
        try {
            Thread.sleep(1000); // Wait for 1 second
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String expectedData = """
                PUT /uploadData HTTP/1.1\r
                Host: testServer\r
                Content-Type: application/json\r
                Content-Length: 116\r
                \r
                {"air_temp":"13.3","cloud":"Partly cloudy","local_date_time_full":"20230715160000","id":"IDS60901","LamportClock":1}""";

        assertEquals(expectedData, stubNetworkHandler.getLastSentData(), "Should send correct PUT request");
    }

    @Test
    public void testRecurrentUploadWeatherData() {
        // Load the weather data
        contentServer.loadWeatherData("src/test/content/input_test.txt");
        contentServer.uploadWeatherData("testServer", 8080);

        // Wait for initial upload
        try {
            Thread.sleep(65000); // Wait for 1 second
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that data is uploaded recurrently
        assertEquals(3, stubNetworkHandler.getSentDataCount(), "Should have sent more data by now");
    }

}
