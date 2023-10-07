package test.aggregation;

import main.aggregation.AggregationServer;
import test.network.StubNetworkHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AggregationServerTest {
    AggregationServer server;
    StubNetworkHandler stubNetworkHandler;

    @BeforeEach
    void setUp() {
        stubNetworkHandler = new StubNetworkHandler();
        server = new AggregationServer(stubNetworkHandler);
    }

    @AfterEach
    void teardown() {
        server.shutdown();
    }

    @Test
    void testGetNotFound() {
        // Simulating GET request without having data in the DataStore
        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "LamportClock: 1\r\n" +
                "StationID: IDS60901\r\n" +
                "\r\n";
        stubNetworkHandler.setSimulatedResponse(getRequest);

        String expectedResponse = "HTTP/1.1 204 No Content\r\n" +
                "LamportClock: 5\r\n" +
                "\r\n";

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));

        System.out.println(responseData);

        // Check if the server response indicates not found
        assertEquals(responseData, expectedResponse);
    }

    @Test
    void testHandleGetRequest() throws InterruptedException {
        // First, put weather data into the DataStore
        String mockWeatherData = "{ \"id\" : \"IDS60901\" }";
        assertTrue(server.processWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request
        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "LamportClock: 2\r\n" +
                "StationID: IDS60901\r\n" +
                "\r\n";
        stubNetworkHandler.setSimulatedResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));
        Thread.sleep(1000);
        // Check if the server response is expected based on setup data
        assertTrue(responseData.contains("IDS60901"));
    }

    @Test
    void testGetWithoutStationId() {
        // First, put weather data into the DataStore
        String mockWeatherData = "{ \"id\" : \"IDS60901\" }";
        assertTrue(server.processWeatherData(mockWeatherData, 1, "Server1"));

        // Simulating GET request without specifying a station ID
        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "LamportClock: 1\r\n" +
                "\r\n";
        stubNetworkHandler.setSimulatedResponse(getRequest);

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, getRequest, false));

        // Check if the server response is expected based on setup data
        assertTrue(responseData.contains("IDS60901"));
    }

    @Test
    void testHandleFirstPutRequest() {
        // Simulating PUT request
        String putRequest = "PUT /weather.json HTTP/1.1\r\n" +
                "User-Agent: ATOMClient/1/0\r\n" +
                "SenderID: 1\r\n" +
                "LamportClock: 1\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: 235\r\n" +
                "\r\n" +
                "{\r\n" +
                "    \"id\" : \"IDS60901\",\r\n" +
                "    \"wind_spd_kt\": 8\r\n" +
                "}";
        stubNetworkHandler.setSimulatedResponse(putRequest);

        String expectedResponse = "HTTP/1.1 201 HTTP_CREATED\r\n" +
                "LamportClock: 5\r\n" +
                "\r\n";

        // Run server logic for single request
        String responseData = server.handleRequest(stubNetworkHandler.sendAndReceiveData("localhost", 8080, putRequest, false));

        // Check if the server response is "200 OK"
        assertEquals(expectedResponse, responseData);
    }

    @Test
    void testShutdown() throws InterruptedException {
        server = mock(AggregationServer.class);
        when(server.isAlive()).thenReturn(true);

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