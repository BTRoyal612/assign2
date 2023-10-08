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

    /**
     * Constructs a LoadBalancer with the given network handler and a list of
     * pre-configured AggregationServer instances.
     * @param networkHandler The network handler for socket communication.
     * @param aggregationServers The list of available AggregationServers.
     */
    public LoadBalancer(NetworkHandler networkHandler, List<AggregationServer> aggregationServers) {
        this.networkHandler = networkHandler;
        this.aggregationServers = new ArrayList<>(aggregationServers);
    }

    /**
     * Retrieves the list of AggregationServer instances managed by this LoadBalancer.
     * @return A list of AggregationServer instances.
     */
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

    /**
     * Starts the LoadBalancer on the specified port. Initializes health
     * check scheduler to periodically verify the status of AggregationServers.
     * @param port The port number on which LoadBalancer listens for requests.
     */
    public void start(int port) {
        System.out.println("Started LoadBalancer on port: " + port);
        networkHandler.startServer(port);

        healthCheckScheduler = Executors.newScheduledThreadPool(1);
        healthCheckScheduler.scheduleAtFixedRate(this::checkServerHealth, 0, 30, TimeUnit.SECONDS);

        initializeShutdownMonitor();

        initializeAcceptThread();
    }

    /**
     * Accepts incoming client connections and forwards them to an available
     * AggregationServer instance.
     */
    private void initializeAcceptThread() {
        acceptThread = new Thread(() -> {
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

    /**
     * Handles the client socket connection, forwards it to the next available
     * AggregationServer, or sends an error if no server is available.
     * @param clientSocket The client socket to handle.
     */
    private void handleClientSocket(Socket clientSocket) {
        try {
            AggregationServer nextServer = getNextAggregationServer();
            if (nextServer != null) {
                // Pass the client socket to the chosen AS.
                nextServer.acceptExternalSocket(clientSocket);
            } else {
                 // No available AS, send an error or null response to the client.
                String errorResponse = "HTTP/1.1 503 Service Unavailable\r\n" +
                                    "LamportClock: -1\r\n" +
                                    "\r\n";
                networkHandler.sendResponseToClient(errorResponse, clientSocket); // Assuming networkHandler is accessible here.
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

    /**
     * Retrieves the next available AggregationServer using a round-robin approach.
     * @return The next AggregationServer, or null if no server is available.
     */
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

    /**
     * Periodically checks the health/status of all AggregationServer instances.
     * Removes any server from the rotation list that's not responding.
     */
    public synchronized void checkServerHealth() {
        Iterator<AggregationServer> iterator = aggregationServers.iterator();
        while (iterator.hasNext()) {
            AggregationServer server = iterator.next();
            if (!server.isAlive()) {
                System.out.println("Remove Aggregation Server on port: " + server.getPort());
                iterator.remove(); // Removes unreachable servers from the list.
            }
        }
    }

    /**
     * Initializes a thread that listens for a shutdown command from the user.
     * Upon receiving the "SHUTDOWN" command, it triggers the shutdown procedure.
     */
    private void initializeShutdownMonitor() {
        Thread monitorThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Enter 'SHUTDOWN' to terminate the LoadBalancer.");
                String input = scanner.nextLine();
                if ("SHUTDOWN".equalsIgnoreCase(input)) {
                    shutdown();
                    scanner.close();
                    break;
                }
            }
        }, "ShutdownMonitorThread");
        monitorThread.start();
    }

    /**
     * Gracefully shuts down the LoadBalancer and all managed AggregationServer instances.
     */
    public void shutdown() {
        System.out.println("Shutting down the LoadBalancer...");

        checkServerHealth();

        // 0. Set the shutdown flag to true to stop the while loop in acceptThread
        shutdown = true;

        // 2. Stop the acceptThread
        if (acceptThread != null) {
            acceptThread.interrupt();  // Interrupt the thread if it's blocked on I/O operations
        }

        // 3. Stop the health check scheduler
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
        }

        // 1. Signal each AggregationServer to shut down gracefully.
        for (AggregationServer server : aggregationServers) {
            server.shutdown();
        }

        // 4. Close Load Balancer
        networkHandler.closeServer();

        System.out.println("LoadBalancer and all managed AggregationServers have been shut down.");
    }

    /**
     * The main method for starting up the LoadBalancer. It also initializes and
     * starts a specified number of AggregationServer instances.
     * @param args Command line arguments, specifying port number and the number of AggregationServers.
     */
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
        }

        // Initialize the LoadBalancer
        LoadBalancer loadBalancer = new LoadBalancer(lbNetworkHandler, serverInstances);

        // Start the LoadBalancer
        loadBalancer.start(port);
    }
}
