package test.content;

import main.content.ContentServer;
import test.network.StubNetworkHandler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String expectedData =
                "PUT /weather.json HTTP/1.1\r\n" +
                "User-Agent: ATOMClient/1/0\r\n" +
                "Host: testServer\r\n" +
                "SenderID: 444c3c63-d0da-4a2f-bfc7-896825043e69\r\n" +
                "LamportClock: 1\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: 99\r\n" +
                "\r\n" +
                "{\"air_temp\":\"13.3\",\"cloud\":\"Partly cloudy\",\"local_date_time_full\":\"20230715160000\",\"id\":\"IDS60901\"}";

        // Regular expression pattern to match everything before and after the ServerID field
        String regex = "(.*?)SenderID: .*?\r\n(.*?$)";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL); // Pattern.DOTALL allows the dot to match newline characters

        String actualData = stubNetworkHandler.getLastSentData();
        Matcher expectedMatcher = pattern.matcher(expectedData);
        Matcher actualMatcher = pattern.matcher(actualData);

        if (expectedMatcher.find() && actualMatcher.find()) {
            // Retrieve the two portions of the expected and actual strings excluding the ServerID field
            String expectedBeforeServerID = expectedMatcher.group(1);
            String expectedAfterServerID = expectedMatcher.group(2);
            String actualBeforeServerID = actualMatcher.group(1);
            String actualAfterServerID = actualMatcher.group(2);

            // Concatenate the two portions for both expected and actual data
            String processedExpectedData = expectedBeforeServerID + expectedAfterServerID;
            String processedActualData = actualBeforeServerID + actualAfterServerID;

            // Assert
            assertEquals(processedExpectedData, processedActualData, "Should send correct PUT request excluding SenderID");
        } else {
            fail("Could not process the expected or actual data properly");
        }
    }
}
