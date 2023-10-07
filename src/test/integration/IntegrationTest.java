package test.integration;

import com.google.gson.JsonObject;
import main.aggregation.AggregationServer;
import main.aggregation.LoadBalancer;
import main.client.GETClient;
import main.content.ContentServer;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class IntegrationTest {

    private LoadBalancer loadBalancer;
    private  List<AggregationServer> serverInstances;
    private ContentServer contentServer1, contentServer2;
    private GETClient getClient1, getClient2;

    @BeforeEach
    public void setUp() {
        NetworkHandler lbNetworkHandler = new SocketNetworkHandler();
        serverInstances = new ArrayList<>();

        // Generate the ports for the Aggregation Servers starting from the default AS port (e.g., 4568)
        int defaultPort = 4567;
        for (int i = 1; i <= 3; i++) {
            int serverPort = defaultPort + i;

            NetworkHandler asNetworkHandler = new SocketNetworkHandler();
            AggregationServer server = new AggregationServer(asNetworkHandler);
            serverInstances.add(server);

            // Start each AggregationServer instance in a new thread
            new Thread(() -> {
                server.start(serverPort);
            }).start();
        }

        // Initialize the LoadBalancer
        loadBalancer = new LoadBalancer(lbNetworkHandler, serverInstances);

        // Start the LoadBalancer
        loadBalancer.start(defaultPort);

        NetworkHandler cs1NetworkHandler = new SocketNetworkHandler();
        contentServer1 = new ContentServer(cs1NetworkHandler);

        NetworkHandler cs2NetworkHandler = new SocketNetworkHandler();
        contentServer2 = new ContentServer(cs2NetworkHandler);

        NetworkHandler c1NetworkHandler = new SocketNetworkHandler();
        getClient1 = new GETClient(c1NetworkHandler);

        NetworkHandler c2NetworkHandler = new SocketNetworkHandler();
        getClient2 = new GETClient(c2NetworkHandler);
    }

    @AfterEach
    public void tearDown() {
        // Shutdown content servers
        contentServer1.shutdown();
        contentServer2.shutdown();

        // Close client connections (assuming there's a close method)
        getClient1.shutdown();
        getClient2.shutdown();

        // Shutdown LoadBalancer (assuming there's a shutdown method)
        loadBalancer.shutdown();

        // Nullify objects to free up resources and make them eligible for garbage collection
        serverInstances.clear();
        loadBalancer = null;
        contentServer1 = null;
        contentServer2 = null;
        getClient1 = null;
        getClient2 = null;
    }

    @Test
    public void testLoadBalancerServerDownRedirection() throws InterruptedException {
        // Simulate server 1 going down
        serverInstances.get(0).shutdown();

        // Use multiple content servers to send data
        assertTrue(contentServer1.loadWeatherData("src/test/integration/input_v1_test.txt"));
        contentServer1.uploadWeatherData("localhost", 4567);

        Thread.sleep(1000);

        assertTrue(contentServer2.loadWeatherData("src/test/integration/input_v2_test.txt"));
        contentServer2.uploadWeatherData("localhost", 4567);

        Thread.sleep(1000);

        // Use multiple GET clients to retrieve data
        JsonObject response1 = getClient1.getData("localhost", 4567, "IDS90210");

        Thread.sleep(1000);

        JsonObject response2 = getClient2.getData("localhost", 4567, "IDS60901");

        // Validate that aggregationServer1 didn't receive any data
        assertNull(serverInstances.get(0).getLastReceivedData());

        // Validate that the other servers did
        assertNotNull(serverInstances.get(1).getLastReceivedData());
        assertNotNull(serverInstances.get(2).getLastReceivedData());

        // Check if the response contains the expected data
        assertEquals("IDS90210", response1.get("id").getAsString());
        assertEquals("Sydney (Darling Harbour / Barangaroo)", response1.get("name").getAsString());
        assertEquals("NSW", response1.get("state").getAsString());
        assertEquals("AEST", response1.get("time_zone").getAsString());
        assertEquals("-33.9", response1.get("lat").getAsString());
        assertEquals("151.2", response1.get("lon").getAsString());
        assertEquals("15/03:00pm", response1.get("local_date_time").getAsString());
        assertEquals("20230715150000", response1.get("local_date_time_full").getAsString());
        assertEquals("20.6", response1.get("air_temp").getAsString());
        assertEquals("18.1", response1.get("apparent_t").getAsString());
        assertEquals("Cloudy", response1.get("cloud").getAsString());
        assertEquals("14.3", response1.get("dewpt").getAsString());
        assertEquals("1012.4", response1.get("press").getAsString());
        assertEquals("68", response1.get("rel_hum").getAsString());
        assertEquals("E", response1.get("wind_dir").getAsString());
        assertEquals("12", response1.get("wind_spd_kmh").getAsString());
        assertEquals("6", response1.get("wind_spd_kt").getAsString());

        assertEquals("IDS60901", response2.get("id").getAsString());
        assertEquals("Adelaide (West Terrace /  ngayirdapira)", response2.get("name").getAsString());
        assertEquals("SA", response2.get("state").getAsString());
        assertEquals("CST", response2.get("time_zone").getAsString());
        assertEquals("-34.9", response2.get("lat").getAsString());
        assertEquals("138.6", response2.get("lon").getAsString());
        assertEquals("15/04:00pm", response2.get("local_date_time").getAsString());
        assertEquals("20230715160000", response2.get("local_date_time_full").getAsString());
        assertEquals("13.3", response2.get("air_temp").getAsString());
        assertEquals("9.5", response2.get("apparent_t").getAsString());
        assertEquals("Partly cloudy", response2.get("cloud").getAsString());
        assertEquals("5.7", response2.get("dewpt").getAsString());
        assertEquals("1023.9", response2.get("press").getAsString());
        assertEquals("60", response2.get("rel_hum").getAsString());
        assertEquals("S", response2.get("wind_dir").getAsString());
        assertEquals("15", response2.get("wind_spd_kmh").getAsString());
        assertEquals("8", response2.get("wind_spd_kt").getAsString());
    }

    @Test
    public void testAllServersDown() {
        // Shutdown all servers
        serverInstances.forEach(AggregationServer::shutdown);

        // Attempt to send data
        assertTrue(contentServer1.loadWeatherData("src/test/integration/input_v1_test.txt"));
        contentServer1.uploadWeatherData("localhost", 4567);

        // Attempt to retrieve data
        JsonObject response = getClient1.getData("localhost", 4567, "IDS90210");

        // Validate that no servers received data
        serverInstances.forEach(server -> assertNull(server.getLastReceivedData()));
        assertNull(response);
    }

    @Test
    public void testLoadBalancerRedistributesOnNewServerAddition() {
        NetworkHandler newServerNetworkHandler = new SocketNetworkHandler();
        AggregationServer newServer = new AggregationServer(newServerNetworkHandler);
        loadBalancer.addServer(newServer);

        new Thread(() -> {
            newServer.start(4571);
        }).start();

        assertTrue(contentServer1.loadWeatherData("src/test/integration/input_v1_test.txt"));
        contentServer1.uploadWeatherData("localhost", 4567);
        getClient2.getData("localhost", 4567, "IDS60901");
        getClient1.getData("localhost", 4567, "IDS60901");
        getClient2.getData("localhost", 4567, "IDS90210");

        System.out.println("server request: " + newServer.getLastReceivedData());

        assertNotNull(newServer.getLastReceivedData());
    }

    @Test
    public void testDuplicateDataEntry() throws InterruptedException {
        assertTrue(contentServer1.loadWeatherData("src/test/integration/input_v1_test.txt"));
        contentServer1.uploadWeatherData("localhost", 4567);
        Thread.sleep(1000);  // Wait a bit for server to fully shut down
        JsonObject response1 = getClient1.getData("localhost", 4567, "IDS60901");

        assertTrue(contentServer2.loadWeatherData("src/test/integration/input_v3_test.txt"));
        contentServer2.uploadWeatherData("localhost", 4567);
        Thread.sleep(1000);  // Wait a bit for server to fully shut down
        JsonObject response2 = getClient1.getData("localhost", 4567, "IDS60901");

        assertNotEquals(response1.get("wind_spd_kt"), response2.get("wind_spd_kt"));
    }

    @Test
    public void testServerRecovery() throws InterruptedException {
        // Simulate server 1 going down and then coming back up
        serverInstances.get(0).shutdown();

        new Thread(() -> {
            serverInstances.get(0).start(4568);
        }).start();

        assertDoesNotThrow(() -> {
            contentServer1.loadWeatherData("src/test/integration/input_v2_test.txt");
            contentServer1.uploadWeatherData("localhost", 4567);
        });

        Thread.sleep(1000);

        getClient2.getData("localhost", 4567, "IDS60901");
        getClient1.getData("localhost", 4567, "IDS60901");
        getClient2.getData("localhost", 4567, "IDS90210");
        getClient1.getData("localhost", 4567, "IDS90210");

        System.out.println("server request: " + serverInstances.get(0).getLastReceivedData());
        assertNotNull(serverInstances.get(0).getLastReceivedData());
    }

}
