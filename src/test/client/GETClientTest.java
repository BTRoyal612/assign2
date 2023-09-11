package test.client;

import main.client.GETClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.network.StubNetworkHandler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GETClientTest {
    private GETClient client;
    private StubNetworkHandler stubNetworkHandler;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeEach
    public void setUp() {
        stubNetworkHandler = new StubNetworkHandler();
        client = new GETClient(stubNetworkHandler);
        System.setOut(new PrintStream(outContent));
    }
    @Test
    public void testInterpretResponse_NullResponse() {
        client.interpretResponse(null);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");

        assertEquals("Error: No response from server.", capturedOutput);
    }

    @Test
    public void testInterpretResponse_InvalidResponseFormat() {
        Map<String, Object> invalidResponse = new HashMap<>();

        client.interpretResponse(invalidResponse);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");

        assertEquals("Error: Invalid response format.", capturedOutput);
    }

    @Test
    public void testInterpretResponse_NotAvailable() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "not available");

        client.interpretResponse(response);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");

        assertEquals("No weather data available.", capturedOutput);
    }

    @Test
    public void testInterpretResponse_Available() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "available");
        // Assuming this is how the method convertJSONToText would convert it:
        response.put("weather", "sunny");

        client.interpretResponse(response);

        // This assumes that "weather: sunny" is a line in the converted text.
        assertTrue(outContent.toString().contains("weather: sunny"));
    }

    @Test
    public void testInterpretResponse_UnknownStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "unknownStatus");

        client.interpretResponse(response);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");


        assertEquals("Error: Unknown response status: unknownStatus", capturedOutput);
    }

    @Test
    public void testGetData_SuccessfulResponse() {
        String expectedResponse = "{ \"status\": \"available\", \"data\": \"Sample weather data.\" }";
        stubNetworkHandler.setSimulatedReceiveResponse(expectedResponse);

        Map<String, Object> response = client.getData("testServer", 8080, "testStation");

        assertNotNull(response);
        assertTrue(response.containsKey("status"));
        assertEquals("available", response.get("status"));
        assertTrue(response.containsKey("data"));
        assertEquals("Sample weather data.", response.get("data"));
    }

    @Test
    public void testGetData_NoDataAvailable() {
        String expectedResponse = "{ \"status\": \"not available\" }";
        stubNetworkHandler.setSimulatedReceiveResponse(expectedResponse);

        Map<String, Object> response = client.getData("testServer", 8080, "testStation");

        assertNotNull(response);
        assertTrue(response.containsKey("status"));
        assertEquals("not available", response.get("status"));
        assertFalse(response.containsKey("data"));
    }

    @Test
    public void testGetData_NullResponse() {
        stubNetworkHandler.setSimulatedReceiveResponse(null);  // Simulate no response

        Map<String, Object> response = client.getData("testServer", 8080, "testStation");

        assertNull(response);
    }

    @Test
    public void testGetData_InvalidResponse() {
        String expectedResponse = "This is not a valid JSON.";
        stubNetworkHandler.setSimulatedReceiveResponse(expectedResponse);

        Map<String, Object> response = client.getData("testServer", 8080, "testStation");

        assertNull(response);
    }
}
