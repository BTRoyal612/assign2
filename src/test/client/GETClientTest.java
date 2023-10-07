package test.client;

import com.google.gson.JsonObject;
import main.client.GETClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.network.StubNetworkHandler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class GETClientTest {
    private GETClient client;
    private StubNetworkHandler stubNetworkHandler;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
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
        stubNetworkHandler = new StubNetworkHandler();
        client = new GETClient(stubNetworkHandler);
        System.setOut(new PrintStream(outContent));
    }
    @Test
    public void testInterpretResponse_NullResponse() {
        client.interpretResponse(null);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");

        assertEquals("", capturedOutput);
    }

    @Test
    public void testInterpretResponse_InvalidResponseFormat() {
        JsonObject invalidResponse = new JsonObject();

        client.interpretResponse(invalidResponse);
        String capturedOutput = outContent.toString().replace(System.lineSeparator(), "");

        assertEquals("", capturedOutput);
    }

    @Test
    public void testGetData_SuccessfulResponse() {
        String expectedResponse = "{ \"status\": \"available\", \"data\": \"Sample weather data.\" }";
        stubNetworkHandler.setSimulatedResponse(expectedResponse);

        JsonObject response = client.getData("testServer", 8080, "testStation");

        assertNotNull(response);
        assertTrue(response.has("status"));
        assertEquals("available", response.get("status").getAsString());
        assertTrue(response.has("data"));
        assertEquals("Sample weather data.", response.get("data").getAsString());
    }

    @Test
    public void testGetData_NoDataAvailable() {
        String expectedResponse = "{ \"status\": \"not available\" }";
        stubNetworkHandler.setSimulatedResponse(expectedResponse);

        JsonObject response = client.getData("testServer", 8080, "testStation");

        assertNotNull(response);
        assertTrue(response.has("status"));
        assertEquals("not available", response.get("status").getAsString());
        assertFalse(response.has("data"));
    }

    @Test
    public void testGetData_NullResponse() {
        stubNetworkHandler.setSimulatedResponse(null);  // Simulate no response

        JsonObject response = client.getData("testServer", 8080, "testStation");

        assertNull(response);
    }

    @Test
    public void testGetData_InvalidResponse() {
        String expectedResponse = "This is not a valid JSON.";
        stubNetworkHandler.setSimulatedResponse(expectedResponse);

        JsonObject response = client.getData("testServer", 8080, "testStation");

        assertNull(response);
    }
}
