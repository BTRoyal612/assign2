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
    public void testUploadWeatherData() {
        // Assuming weather data is loaded first
        contentServer.loadWeatherData("src/test/content/input_test.txt");

        String response = contentServer.uploadWeatherData("testServer", 8080);
        String expectedData = """
                PUT /uploadData HTTP/1.1\r
                Content-Type: application/json\r
                Content-Length: 116\r
                \r
                {"air_temp":"13.3","cloud":"Partly cloudy","local_date_time_full":"20230715160000","id":"IDS60901","LamportClock":1}""";

        assertEquals(expectedData, stubNetworkHandler.getLastSentData(), "Should send correct PUT request");
        assertEquals("200 OK", response, "Should receive a 200 OK response from stub");
    }

//    @Test
//    public void testSendHeartbeat() {
//        // Ensure the stub will respond correctly to the heartbeat
//        stubNetworkHandler.setSimulatedSendResponse("200 OK");
//
//        // Invoke sendHeartbeat
//        contentServer.sendHeartbeat("testServer", 8080);
//
//        // Give it a little time to send the initial heartbeat
//        try {
//            Thread.sleep(100); // Wait for 20 seconds
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        // Verify initial heartbeat
//        assertTrue(stubNetworkHandler.getLastSentData().startsWith("HEARTBEAT"), "Should send correct initial heartbeat message");
//
//        // Let's test two more intervals to see if it sends heartbeats recurrently
//        try {
//            Thread.sleep(16000); // Wait for 20 seconds
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//        // Verify that heartbeat continues
//        assertTrue(stubNetworkHandler.getSentDataCount() > 1, "Should have sent more than one heartbeat by now");
//    }
}
