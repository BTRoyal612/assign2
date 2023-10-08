package test.aggregation;

import main.aggregation.AggregationServer;
import main.aggregation.LoadBalancer;
import main.network.NetworkHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadBalancerTest {

    private NetworkHandler mockNetworkHandler;
    private AggregationServer mockServer1, mockServer2, mockServer3;
    private List<AggregationServer> mockServerList;
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
        // Setup mock NetworkHandler and AggregationServers
        mockNetworkHandler = mock(NetworkHandler.class);
        mockServer1 = mock(AggregationServer.class);
        mockServer2 = mock(AggregationServer.class);
        mockServer3 = mock(AggregationServer.class);
        mockServerList = Arrays.asList(mockServer1, mockServer2, mockServer3);

        when(mockServer1.isAlive()).thenReturn(true);
        when(mockServer2.isAlive()).thenReturn(true); // Pretend server2 is not alive
        when(mockServer3.isAlive()).thenReturn(true);
    }

    @Test
    public void testGetNextAggregationServer() {
        LoadBalancer lb = new LoadBalancer(mockNetworkHandler, mockServerList);

        // Test round-robin behavior
        assertSame(mockServer1, lb.getNextAggregationServer());
        assertSame(mockServer2, lb.getNextAggregationServer());
        assertSame(mockServer3, lb.getNextAggregationServer());
        assertSame(mockServer1, lb.getNextAggregationServer());
    }

    @Test
    public void testCheckServerHealth() {
        LoadBalancer lb = new LoadBalancer(mockNetworkHandler, mockServerList);

        // Mock behaviors for the isAlive method
        when(mockServer2.isAlive()).thenReturn(false); // Pretend server2 is not alive

        lb.checkServerHealth();

        // Since server2 is not alive, it should be removed from the list.
        List<AggregationServer> updatedServers = lb.getAggregationServers(); // Add a getter for this in LoadBalancer for the test's sake

        assertTrue(updatedServers.contains(mockServer1));
        assertFalse(updatedServers.contains(mockServer2));
        assertTrue(updatedServers.contains(mockServer3));
    }

    @Test
    public void testAddNewServer() {
        LoadBalancer lb = new LoadBalancer(mockNetworkHandler, mockServerList);

        // Create a new mock server and add to the load balancer
        AggregationServer mockServer4 = mock(AggregationServer.class);
        when(mockServer4.isAlive()).thenReturn(true);
        lb.addServer(mockServer4);  // Assuming you've a method to add servers

        // Rotate through the servers
        lb.getNextAggregationServer();  // mockServer1
        lb.getNextAggregationServer();  // mockServer2
        lb.getNextAggregationServer();  // mockServer3
        AggregationServer result = lb.getNextAggregationServer();  // mockServer4

        assertSame(mockServer4, result);
    }

    @Test
    public void testRemoveServer() {
        LoadBalancer lb = new LoadBalancer(mockNetworkHandler, mockServerList);

        // Remove mockServer2
        lb.removeServer(mockServer2);  // Assuming you've a method to remove servers

        // Rotate through the servers
        lb.getNextAggregationServer();  // mockServer1
        AggregationServer result = lb.getNextAggregationServer();  // mockServer3 (since mockServer2 is removed)

        assertSame(mockServer3, result);
    }

    @Test
    public void testEmptyServerListHandling() {
        LoadBalancer lb = new LoadBalancer(mockNetworkHandler, new ArrayList<>());  // Empty server list

        // Try getting a server
        AggregationServer result = lb.getNextAggregationServer();

        assertNull(result, "Should return null or handle gracefully when no servers are present");
    }
}
