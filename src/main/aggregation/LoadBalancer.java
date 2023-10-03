package main.aggregation;

import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LoadBalancer {
    private static final int DEFAULT_PORT = 4567; // For LB. Different from AggregationServer.
    private final List<InetSocketAddress> serverAddresses;
    private volatile boolean shutdown = false;
    private NetworkHandler networkHandler; // This will be the same type as the one in AggregationServer.
    private int serverIndex = 0;
    private ScheduledExecutorService healthCheckScheduler;

    private List<AggregationServer> managedServers;

    public LoadBalancer(NetworkHandler networkHandler, List<InetSocketAddress> serverAddresses) {
        this.networkHandler = networkHandler;
        this.serverAddresses = serverAddresses;
        this.managedServers = new ArrayList<>();
    }

    public void start(int port) {
        networkHandler.startServer(port);
        initializeAcceptThread();

        healthCheckScheduler = Executors.newScheduledThreadPool(1);
        healthCheckScheduler.scheduleAtFixedRate(this::checkServerHealth, 0, 10, TimeUnit.SECONDS);

        initializeShutdownMonitor();
    }

    public void addManagedServer(AggregationServer server) {
        this.managedServers.add(server);
    }

    private void initializeAcceptThread() {
        Thread acceptThread = new Thread(() -> {
            while (!shutdown) {
                try {
                    Socket clientSocket = networkHandler.acceptConnection();
                    if (clientSocket != null) {
                        handleClientSocket(clientSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        acceptThread.start();
    }

    private void handleClientSocket(Socket clientSocket) {
        try {
            String requestData = networkHandler.waitForClientData(clientSocket);
            if (requestData != null) {
                InetSocketAddress serverAddress = getNextAggregationServer();
                if (serverAddress != null) {
                    String responseData = forwardRequestToAggregationServer(serverAddress, requestData);
                    networkHandler.sendResponseToClient(responseData, clientSocket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized InetSocketAddress getNextAggregationServer() {
        if (serverAddresses.isEmpty()) return null;

        serverIndex = (serverIndex + 1) % serverAddresses.size();
        return serverAddresses.get(serverIndex);
    }

    public String forwardRequestToAggregationServer(InetSocketAddress serverAddress, String requestData) {
        Socket serverSocket = null;
        try {
            // Establish a connection to the AggregationServer.
            serverSocket = networkHandler.createConnection(serverAddress);

            // Check if the socket is valid (in case the connection failed and returned null)
            if (serverSocket == null) {
                return "Error: Failed to establish connection to server";
            }

            // Forward the client's request to the server.
            networkHandler.sendData(serverSocket, requestData);

            // Get the server's response.
            String serverResponse = networkHandler.receiveData(serverSocket);
            return serverResponse;
        } catch (Exception e) { // Catching general exceptions since IOException is no longer thrown directly
            e.printStackTrace();
            return "Error: Failed to forward request to server";
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void checkServerHealth() {
        Iterator<InetSocketAddress> iterator = serverAddresses.iterator();
        while (iterator.hasNext()) {
            InetSocketAddress address = iterator.next();
            if (!isServerAlive(address)) {
                iterator.remove(); // Removes unreachable servers from the list.
            }
        }
    }

    private boolean isServerAlive(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            // Try to establish a connection with a timeout (e.g., 2 seconds)
            socket.connect(address, 2000);
            return true;
        } catch (IOException e) {
            // If an exception occurs during connection, it indicates the server is down or unreachable.
            return false;
        }
    }

    private void initializeShutdownMonitor() {
        Thread monitorThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Enter 'SHUTDOWN' to terminate the LoadBalancer.");
                String input = scanner.nextLine();
                if ("SHUTDOWN".equalsIgnoreCase(input)) {
                    shutdownLoadBalancer();
                    scanner.close();
                    break;
                }
            }
        }, "ShutdownMonitorThread");
        monitorThread.start();
    }

    private void shutdownLoadBalancer() {
        System.out.println("Shutting down the LoadBalancer...");

        // 1. Signal each AggregationServer to shut down gracefully.
        for (AggregationServer server : managedServers) {
            server.shutdown(); // Assuming the AggregationServer has a shutdown method
        }

        // 2. Close all active connections.
        // (This depends on the implementation of your NetworkHandler and LoadBalancer)
        // E.g.:
        // networkHandler.closeAllConnections(); // Assuming there's a closeAllConnections() method

        // 3. Perform cleanup operations. This can include stopping threads or other resources.
        // E.g., if you've any executors or schedulers:
        // myExecutor.shutdownNow();

        System.out.println("LoadBalancer and all managed AggregationServers have been shut down.");

        // 4. Exit the program
        System.exit(0);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        // Start the LoadBalancer's network handler
        NetworkHandler lbNetworkHandler = new SocketNetworkHandler();

        // Define the ports and addresses for the Aggregation Servers
        List<Integer> serverPorts = Arrays.asList(4568, 4569, 4570);
        List<InetSocketAddress> servers = new ArrayList<>();

        for (int serverPort : serverPorts) {
            servers.add(new InetSocketAddress("127.0.0.1", serverPort));
        }

        // Initialize the LoadBalancer
        LoadBalancer loadBalancer = new LoadBalancer(lbNetworkHandler, servers);

        for (int serverPort : serverPorts) {
            NetworkHandler asNetworkHandler = new SocketNetworkHandler();
            AggregationServer server = new AggregationServer(asNetworkHandler);
            server.start(serverPort); // Start each AggregationServer instance

            // Add to managed servers
            loadBalancer.addManagedServer(server);
        }

        // Start the LoadBalancer
        loadBalancer.start(port);
    }
}
