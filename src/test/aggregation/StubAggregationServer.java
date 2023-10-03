package test.aggregation;

import java.net.InetSocketAddress;

public class StubAggregationServer {
    private final InetSocketAddress address;

    public StubAggregationServer(InetSocketAddress address) {
        this.address = address;
    }

    public void start() {
        // Simulate starting up, maybe initializing some internal state or data.
    }

    public void stop() {
        // Clean up or reset the stub server's state.
    }

    // Any other methods to help simulate the behavior or check the state of the stub server...
}
