package test.aggregation;

import main.aggregation.AggregationServer;
import org.json.JSONObject;
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
        JSONObject mockWeatherData = new JSONObject("{ \"id\" : \"IDS60901\" }");
        assertTrue(server.addWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request
        String getRequest = """
                GET /weather.json HTTP/1.1\r
                LamportClock: 2\r
                StationID: IDS60901\r
                \r
                """;
        stubNetworkHandler.setSimulatedReceiveResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.receiveData("localhost", 8080, getRequest));

        // Check if the server response is expected based on setup data
        assertTrue(responseData.contains("IDS60901"));
    }

    @Test
    void testGetWithoutStationId() {
        // First, put weather data into the DataStore
        JSONObject mockWeatherData = new JSONObject("{ \"id\" : \"IDS60901\" }");
        assertTrue(server.addWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request without specifying a station ID
        String getRequest = """
                GET /weather.json HTTP/1.1\r
                LamportClock: 1\r
                \r
                """;
        stubNetworkHandler.setSimulatedReceiveResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.receiveData("localhost", 8080, getRequest));

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
        stubNetworkHandler.setSimulatedReceiveResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.receiveData("localhost", 8080, getRequest));

        // Check if the server response indicates not found
        assertEquals("404 Not Found", responseData);
    }

    @Test
    void testHandlePutRequest() {
        // Simulating PUT request
        String putRequest = """
                PUT /weather.json HTTP/1.1\r
                User-Agent: ATOMClient/1/0\r
                ServerID: 1\r
                Content-Type: application/json\r
                Content-Length: 235\r
                \r
                {\r
                    "id" : "IDS60901",\r
                    "wind_spd_kt": 8\r
                }""";
        stubNetworkHandler.setSimulatedReceiveResponse(putRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.receiveData("localhost", 8080, putRequest));

        // Check if the server response is "200 OK"
        assertEquals("200 OK", responseData);
    }

    @Test
    void testShutdown() {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> server.start(8080));
        serverThread.start();

        // Simulate waiting for some time
        try {
            Thread.sleep(500);  // wait for 0.5 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Send shutdown command
        server.shutdown();

        // Check if server is shut down
        assertFalse(serverThread.isAlive());  // This is a simplified check, more checks might be needed
    }
}
