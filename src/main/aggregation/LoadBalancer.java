package main.aggregation;

import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LoadBalancer {
    private static final int DEFAULT_PORT = 4567; // For LB. Different from AggregationServer.
    private volatile boolean shutdown = false;
    private NetworkHandler networkHandler; // This will be the same type as the one in AggregationServer.
    private int serverIndex = 0;
    private ScheduledExecutorService healthCheckScheduler;

    private List<AggregationServer> aggregationServers;

    public LoadBalancer(NetworkHandler networkHandler, List<AggregationServer> aggregationServers) {
        this.networkHandler = networkHandler;
        this.aggregationServers = new ArrayList<>(aggregationServers);
    }

    public void start(int port) {
        System.out.println("start lb");
        networkHandler.startServer(port);

        System.out.println("start lb health check");
        healthCheckScheduler = Executors.newScheduledThreadPool(1);
        healthCheckScheduler.scheduleAtFixedRate(this::checkServerHealth, 0, 30, TimeUnit.SECONDS);

        System.out.println("start shutdown monitor");
        initializeShutdownMonitor();

        System.out.println("start request listen");
        initializeAcceptThread();
    }

    private void initializeAcceptThread() {
        Thread acceptThread = new Thread(() -> {
            while (!shutdown) {
                try {
                    Socket clientSocket = networkHandler.acceptConnection();
                    if (clientSocket != null) {
                        System.out.println("lb receive request");
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
            AggregationServer nextServer = getNextAggregationServer();
            if (nextServer != null) {
                // Pass the client socket to the chosen AS.
                nextServer.acceptExternalSocket(clientSocket);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public synchronized AggregationServer getNextAggregationServer() {
        if (aggregationServers.isEmpty()) {
            return null; // Return null or handle this scenario as per your needs.
        }

        AggregationServer nextServer = aggregationServers.get(serverIndex);
        serverIndex = (serverIndex + 1) % aggregationServers.size(); // This ensures round-robin behavior
        return nextServer;
    }

    public void checkServerHealth() {
        Iterator<AggregationServer> iterator = aggregationServers.iterator();
        while (iterator.hasNext()) {
            AggregationServer server = iterator.next();
            if (!server.isAlive()) {
                iterator.remove(); // Removes unreachable servers from the list.
            }
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
        for (AggregationServer server : aggregationServers) {
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
        List<AggregationServer> serverInstances = new ArrayList<>();

        // Define the ports for the Aggregation Servers
        List<Integer> serverPorts = Arrays.asList(4568, 4569, 4570);

        for (int serverPort : serverPorts) {
            NetworkHandler asNetworkHandler = new SocketNetworkHandler();
            AggregationServer server = new AggregationServer(asNetworkHandler);
            serverInstances.add(server);

            // Start each AggregationServer instance in a new thread
            new Thread(() -> {
                server.start(serverPort);
            }).start();

            System.out.println("Started AggregationServer on port: " + serverPort);
        }

        // Initialize the LoadBalancer
        LoadBalancer loadBalancer = new LoadBalancer(lbNetworkHandler, serverInstances);

        // Start the LoadBalancer
        loadBalancer.start(port);
    }

    public List<AggregationServer> getAggregationServers() {
        return this.aggregationServers;
    }
}
