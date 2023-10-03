package test.aggregation;

import main.aggregation.AggregationServer;
import main.aggregation.LoadBalancer;
import main.network.NetworkHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

public class LoadBalancerTest {

    private NetworkHandler mockNetworkHandler;
    private AggregationServer mockServer1, mockServer2, mockServer3;
    private List<AggregationServer> mockServerList;

    @BeforeEach
    public void setUp() {
        // Setup mock NetworkHandler and AggregationServers
        mockNetworkHandler = mock(NetworkHandler.class);
        mockServer1 = mock(AggregationServer.class);
        mockServer2 = mock(AggregationServer.class);
        mockServer3 = mock(AggregationServer.class);
        mockServerList = Arrays.asList(mockServer1, mockServer2, mockServer3);
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
        when(mockServer1.isAlive()).thenReturn(true);
        when(mockServer2.isAlive()).thenReturn(false); // Pretend server2 is not alive
        when(mockServer3.isAlive()).thenReturn(true);

        lb.checkServerHealth();

        // Since server2 is not alive, it should be removed from the list.
        List<AggregationServer> updatedServers = lb.getAggregationServers(); // Add a getter for this in LoadBalancer for the test's sake

        assertTrue(updatedServers.contains(mockServer1));
        assertFalse(updatedServers.contains(mockServer2));
        assertTrue(updatedServers.contains(mockServer3));
    }
}
