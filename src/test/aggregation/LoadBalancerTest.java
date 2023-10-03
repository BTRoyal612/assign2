package test.aggregation;

import main.aggregation.LoadBalancer;
import org.junit.After;
import test.network.StubNetworkHandler;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadBalancerTest {
    private LoadBalancer loadBalancer;
    private StubNetworkHandler stubNetworkHandler;
    private List<StubAggregationServer> stubServers;

    @Before
    public void setUp() {
        stubNetworkHandler = new StubNetworkHandler();
        List<InetSocketAddress> serverAddresses = Arrays.asList(
                new InetSocketAddress("127.0.0.1", 4568),
                new InetSocketAddress("127.0.0.1", 4569),
                new InetSocketAddress("127.0.0.1", 4570)
        );

        stubServers = new ArrayList<>();
        for (InetSocketAddress address : serverAddresses) {
            StubAggregationServer stubServer = new StubAggregationServer(address);
            stubServers.add(stubServer);
            stubServer.start();
        }

        loadBalancer = new LoadBalancer(stubNetworkHandler, serverAddresses);
    }

    @After
    public void tearDown() {
        for (StubAggregationServer server : stubServers) {
            server.stop(); // Or some equivalent to shut down the stub server
        }
    }

    @Test
    public void testRoundRobinDistribution() {
        InetSocketAddress firstServer = loadBalancer.getNextAggregationServer();
        InetSocketAddress secondServer = loadBalancer.getNextAggregationServer();
        InetSocketAddress thirdServer = loadBalancer.getNextAggregationServer();
        InetSocketAddress loopBackServer = loadBalancer.getNextAggregationServer();

        assertEquals(firstServer, new InetSocketAddress("127.0.0.1", 4568));
        assertEquals(secondServer, new InetSocketAddress("127.0.0.1", 4569));
        assertEquals(thirdServer, new InetSocketAddress("127.0.0.1", 4570));
        assertEquals(loopBackServer, firstServer);  // Checks the round-robin mechanism
    }

    @Test
    public void testDataFlow() {
        String clientData = "Client Data";
        stubNetworkHandler.setSimulatedResponse("Aggregation Server Response");

        String response = loadBalancer.forwardRequestToAggregationServer(
                new InetSocketAddress("127.0.0.1", 4568), clientData
        );

        assertEquals("Aggregation Server Response", response);
        assertEquals(clientData, stubNetworkHandler.getLastSentData());
        assertEquals(1, stubNetworkHandler.getSentDataCount());
    }
}
