package test.aggregation;

import com.google.gson.JsonObject;
import main.aggregation.AggregationServer;
import main.common.JSONHandler;
import org.junit.jupiter.api.*;
import test.network.StubNetworkHandler;

import static org.junit.jupiter.api.Assertions.*;

class AggregationServerTest {
    AggregationServer server;
    StubNetworkHandler stubNetworkHandler;

    @BeforeEach
    void setUp() {
        stubNetworkHandler = new StubNetworkHandler();
        server = new AggregationServer(stubNetworkHandler);
    }

    @Test
    void testHandleGetRequest() {
        // First, put weather data into the DataStore
        JsonObject mockWeatherData = JSONHandler.parseJSONObject("{ \"id\" : \"IDS60901\" }");
        assertTrue(server.addWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request
        String getRequest = """
                GET /weather.json HTTP/1.1\r
                LamportClock: 2\r
                StationID: IDS60901\r
                \r
                """;
        stubNetworkHandler.setSimulatedResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));

        // Check if the server response is expected based on setup data
        assertTrue(responseData.contains("IDS60901"));
    }

    @Test
    void testGetWithoutStationId() {
        // First, put weather data into the DataStore
        JsonObject mockWeatherData = JSONHandler.parseJSONObject("{ \"id\" : \"IDS60901\" }");
        assertTrue(server.addWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request without specifying a station ID
        String getRequest = """
                GET /weather.json HTTP/1.1\r
                LamportClock: 1\r
                \r
                """;
        stubNetworkHandler.setSimulatedResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));

        // Check if the server response is expected based on setup data
        assertTrue(responseData.contains("IDS60901"));
    }

    @Test
    void testGetNotFound() {
        // Simulating GET request without having data in the DataStore
        String getRequest = """
                GET /weather.json HTTP/1.1\r
                LamportClock: 1\r
                StationID: IDS60901\r
                \r
                """;
        stubNetworkHandler.setSimulatedResponse(getRequest);

        String expectedResponse = """
                HTTP/1.1 204 No Content\r
                LamportClock: 3\r
                \r
                """;

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));

        // Check if the server response indicates not found
        assertEquals(expectedResponse, responseData);
    }

    @Test
    void testHandleFirstPutRequest() {
        // Simulating PUT request
        String putRequest = """
                PUT /weather.json HTTP/1.1\r
                User-Agent: ATOMClient/1/0\r
                SenderID: 1\r
                Content-Type: application/json\r
                Content-Length: 235\r
                \r
                {\r
                    "id" : "IDS60901",\r
                    "wind_spd_kt": 8\r
                }""";
        stubNetworkHandler.setSimulatedResponse(putRequest);

        String expectedResponse = """
                HTTP/1.1 201 HTTP_CREATED\r
                LamportClock: 2\r
                \r
                """;

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, putRequest, false));

        // Check if the server response is "200 OK"
        assertEquals(expectedResponse, responseData);
    }

    @Test
    void testShutdown() throws InterruptedException {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> server.start(8080));
        serverThread.start();

        // Simulate waiting for some time
        Thread.sleep(500);  // wait for 0.5 seconds

        // Send shutdown command
        server.shutdown();

        serverThread.join(2000);  // Wait up to 2 seconds for server thread to terminate

        // Check if server is shut down
        assertFalse(serverThread.isAlive());
    }
}
