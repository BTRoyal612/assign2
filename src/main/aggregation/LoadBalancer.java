package main.aggregation;

import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LoadBalancer {
    private static final int DEFAULT_PORT = 4567;
    private volatile boolean shutdown = false;
    private int serverIndex = 0;
    private Thread acceptThread;
    private NetworkHandler networkHandler;
    private ScheduledExecutorService healthCheckScheduler;
    private List<AggregationServer> aggregationServers;

    public LoadBalancer(NetworkHandler networkHandler, List<AggregationServer> aggregationServers) {
        this.networkHandler = networkHandler;
        this.aggregationServers = new ArrayList<>(aggregationServers);
    }

    public List<AggregationServer> getAggregationServers() {
        return this.aggregationServers;
    }

    /**
     * Adds a new server to the LoadBalancer's rotation.
     * @param server The server to be added.
     */
    public void addServer(AggregationServer server) {
        if (server != null && !aggregationServers.contains(server)) {
            aggregationServers.add(server);
        }
    }

    /**
     * Removes a server from the LoadBalancer's rotation.
     * @param server The server to be removed.
     */
    public void removeServer(AggregationServer server) {
        aggregationServers.remove(server);
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
        acceptThread = new Thread(() -> {
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

        AggregationServer nextServer;
        int startIndex = serverIndex; // To prevent infinite loops if none of the servers are alive.

        do {
            nextServer = aggregationServers.get(serverIndex);
            serverIndex = (serverIndex + 1) % aggregationServers.size(); // This ensures round-robin behavior
            if (nextServer.isAlive()) {
                return nextServer;
            }
        } while (serverIndex != startIndex); // Ensures we only loop through the servers once.

        return null; // None of the servers are alive.
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

        // 0. Set the shutdown flag to true to stop the while loop in acceptThread
        shutdown = true;

        // 1. Signal each AggregationServer to shut down gracefully.
        for (AggregationServer server : aggregationServers) {
            server.shutdown();
        }

        // 2. Stop the acceptThread
        if (acceptThread != null) {
            acceptThread.interrupt();  // Interrupt the thread if it's blocked on I/O operations
        }

        // 3. Stop the health check scheduler
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
        }

        // 4. Close Load Balancer
        networkHandler.closeServer();

        System.out.println("LoadBalancer and all managed AggregationServers have been shut down.");

        // 5. Exit the program
        System.exit(0);
    }

    public static void main(String[] args) {
        // Default values
        int port = DEFAULT_PORT;
        int numberOfAS = 3;  // Default to 3 AS instances

        // Parse the command line arguments
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            numberOfAS = Integer.parseInt(args[1]);
        }

        // Validate the number of AS
        if (numberOfAS <= 0) {
            System.out.println("The number of AggregationServers should be greater than 0.");
            return;
        }

        // Start the LoadBalancer's network handler
        NetworkHandler lbNetworkHandler = new SocketNetworkHandler();
        List<AggregationServer> serverInstances = new ArrayList<>();

        // Generate the ports for the Aggregation Servers starting from the default AS port (e.g., 4568)
        int defaultASPort = port;
        for (int i = 1; i <= numberOfAS; i++) {
            int serverPort = defaultASPort + i;

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
}
