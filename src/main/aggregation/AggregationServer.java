package main.aggregation;

import com.google.gson.*;

import main.common.JsonHandler;
import main.common.WeatherData;
import main.common.LamportClock;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.io.PrintWriter;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final long THRESHOLD = 40000;
    private static LamportClock sharedClock = new LamportClock();
    private static AtomicInteger asCount = new AtomicInteger();
    private static DataStoreService dataStoreService = DataStoreService.getInstance();
    private volatile boolean shutdown = false;
    private int port;
    private Thread acceptThread;
    private LamportClock lamportClock;
    private NetworkHandler networkHandler;
    private LinkedBlockingQueue<Socket> requestQueue;
    private String lastReceivedData = null;

    /**
     * Constructor for AggregationServer.
     * @param networkHandler The network handler responsible for handling server's network interactions.
     */
    public AggregationServer(NetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
        this.requestQueue = new LinkedBlockingQueue<>();

        this.lamportClock = new LamportClock();
        int sharedTime = sharedClock.getTime();
        int localTime = lamportClock.getTime();

        if (sharedTime > localTime) {
            lamportClock.setClock(sharedTime);
        }

        dataStoreService.registerAS();
        asCount.incrementAndGet();
    }

    public int getPort() {
        return this.port;
    }

    public String getLastReceivedData() {
        return lastReceivedData;
    }

    private void setLastReceivedData(String data) {
        this.lastReceivedData = data;
    }

    /**
     * A simple health check method to check if the AggregationServer is up and running.
     * @return true if the server is reachable, false otherwise.
     */
    public boolean isAlive() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 1000); // 1 second timeout
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void synchronizeWithSharedClock() {
        int sharedTime = sharedClock.getTime();
        int localTime = lamportClock.getTime();

        // Update local clock if the shared clock has a greater value
        if (sharedTime > localTime) {
            lamportClock.setClock(sharedTime);
        }

        // Update the shared clock with the local clock's time.
        // This ensures if the local clock had a greater value, the shared clock is updated.
        sharedClock.receive(lamportClock.getTime());
        lamportClock.tick();
    }

    /**
     * Starts the server and initializes required components.
     * @param portNumber The port number on which the server will listen for incoming connections.
     */
    public void start(int portNumber) {
        System.out.println("Started AggregationServer on port: " + portNumber);
        this.port = portNumber;
        networkHandler.startServer(portNumber); // Starting the server socket

        processClientRequests();                // Start processing client requests
    }

    public void startAlone(int portNumber) {
        System.out.println("Started AggregationServer on port: " + portNumber);
        this.port = portNumber;
        networkHandler.startServer(portNumber); // Starting the server socket

        initializeShutdownMonitor();            // Start the shutdown monitor thread

        initializeAcceptThread();               // Start the client acceptance thread

        processClientRequests();                // Start processing client requests
    }

    /**
     * This method is used by the LoadBalancer to directly inject a client socket into the
     * Aggregation Server's processing logic.
     * @param clientSocket The client socket forwarded by the LoadBalancer.
     */
    public void acceptExternalSocket(Socket clientSocket) {
        try {
            System.out.println(getPort() + " received external socket from LoadBalancer: " + clientSocket);

            // Send the current Lamport clock value to the client.
            String clockValue = "LamportClock: " + lamportClock.getTime();
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(clockValue);
            out.flush();

            requestQueue.put(clientSocket);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes a monitor thread that listens for SHUTDOWN command from the console to shut down the server.
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
        });
        monitorThread.start();
    }

    /**
     * Initiates the server shutdown sequence, interrupting the client acceptance thread.
     */
    public void shutdown() {
        this.shutdown = true;

        // Interrupt the acceptThread to break the potential blocking call
        if(acceptThread != null) {
            acceptThread.interrupt();
        }

        networkHandler.closeServer();

        dataStoreService.deregisterAS();

        if (asCount.decrementAndGet() == 0) {
            sharedClock.setClock(0);
        }

        System.out.println("Shutting down AggregationServer on port " + getPort());
    }

    /**
     * Initializes a thread to continuously accept incoming client connections and add them to a request queue.
     */
    private void initializeAcceptThread() {
        acceptThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = networkHandler.acceptConnection();
                    if(clientSocket != null) {
                        System.out.println("Accepted connection from: " + clientSocket);

                        // Send the current Lamport clock value to the client right after accepting the connection
                        String clockValue = "LamportClock: " + lamportClock.getTime();
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println(clockValue);
                        out.flush();

                        requestQueue.put(clientSocket);
                    }
                } catch (IOException e) {
                    if(Thread.currentThread().isInterrupted()){
                        // The thread was interrupted during the blocking acceptConnection call
                        break;
                    }
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        acceptThread.start();
    }

    /**
     * Continuously processes incoming client requests until the server is shut down.
     */
    private void processClientRequests() {
        try {
            while (!shutdown) {
                Socket clientSocket = waitForClient();
                if (clientSocket != null) {
                    handleClientSocket(clientSocket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            networkHandler.closeServer();
        }
    }

    /**
     * Waits for an incoming client connection from the request queue.
     * @return A socket representing the client connection or null if server is shutting down.
     * @throws InterruptedException If the waiting thread is interrupted.
     */
    private Socket waitForClient() throws InterruptedException {
        if(shutdown) return null;
        return requestQueue.poll(10, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles communication with a connected client, including reading request data and sending a response.
     * @param clientSocket The socket through which the client is connected.
     */
    private void handleClientSocket(Socket clientSocket) {
        try {
            String requestData = networkHandler.waitForClientData(clientSocket);
            System.out.println(requestData);
            System.out.println();
            if (requestData != null) {
                String responseData = handleRequest(requestData);
                networkHandler.sendResponseToClient(responseData, clientSocket);
            }
        } catch(Exception e) {
            e.printStackTrace(); // Depending on your use-case, you might want to handle this differently.
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Processes a given client request and returns an appropriate response.
     * @param requestData The client's request data as a string.
     * @return A string representing the server's response.
     */
    public String handleRequest(String requestData) {
        setLastReceivedData(requestData);
        String[] lines = requestData.split("\r\n");
        String requestType = lines[0].split(" ")[0].trim();

        Map<String, String> headers = new HashMap<>();
        StringBuilder contentBuilder = new StringBuilder();

        boolean readingContent = false;

        for (int i = 1; i < lines.length; i++) {
            if (!readingContent) {
                if (lines[i].isEmpty()) {
                    readingContent = true;
                } else {
                    String[] headerParts = lines[i].split(": ", 2);
                    headers.put(headerParts[0], headerParts[1]);
                }
            } else {
                contentBuilder.append(lines[i]);
            }
        }

        String content = contentBuilder.toString();

        if ("GET".equalsIgnoreCase(requestType)) {
            return handleGetRequest(headers);
        } else if ("PUT".equalsIgnoreCase(requestType)) {
            return handlePutRequest(headers, content);
        } else {
            return formatHttpResponse("400 Bad Request", null);
        }
    }

    private int getLamportTimeFromHeaders(Map<String, String> headers) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);
        synchronizeWithSharedClock();
        return lamportClock.getTime();
    }

    /**
     * Processes a GET request and returns an appropriate response.
     *
     * @param headers A map containing request headers.
     * @return A string representing the server's response.
     */
    public String handleGetRequest(Map<String, String> headers) {
        int lamportTime = getLamportTimeFromHeaders(headers);

        // Retrieve station ID from headers or use default if not provided.
        String stationId = getStationIdFromHeadersOrDefault(headers);
        if (stationId == null) {
            return formatHttpResponse("204 No Content", null);
        }

        // Get weather data for the specified station ID.
        PriorityQueue<WeatherData> weatherDataQueue = dataStoreService.getData(stationId);
        if (isWeatherDataQueueEmpty(weatherDataQueue)) {
            return formatHttpResponse("204 No Content", null);
        }

        // Retrieve the first WeatherData with a Lamport time less than or equal to the request's Lamport time.
        Optional<WeatherData> targetData = getWeatherDataForLamportTime(weatherDataQueue, lamportTime);

        return targetData
                .map(weatherData -> formatHttpResponse("200 OK", weatherData.getData()))
                .orElse(formatHttpResponse("204 No Content", null));
    }

    private boolean isWeatherDataQueueEmpty(PriorityQueue<WeatherData> queue) {
        return queue == null || queue.isEmpty();
    }

    private String getStationIdFromHeadersOrDefault(Map<String, String> headers) {
        String stationId = headers.get("StationID");
        if (stationId != null && !stationId.isEmpty()) {
            return stationId;
        }
        return dataStoreService.getAllDataKeys().stream().findFirst().orElse(null);
    }

    private Optional<WeatherData> getWeatherDataForLamportTime(PriorityQueue<WeatherData> queue, int lamportTime) {
        return queue.stream()
                .filter(data -> data.getLamportTime() <= lamportTime)  // This line ensures the data is less than or equal to provided clock
                .max(Comparator.comparingInt(WeatherData::getLamportTime));  // This line finds the largest among those
    }

    /**
     * Processes a PUT request and returns an appropriate response.
     *
     * @param headers A map containing request headers.
     * @param content The content/body of the request.
     * @return A string representing the server's response.
     */
    private String handlePutRequest(Map<String, String> headers, String content) {
        int lamportTime = getLamportTimeFromHeaders(headers);

        String senderID = headers.get("SenderID");
        if (isValidSender(senderID)) {
            if (processWeatherData(content, lamportTime, senderID)) {
                return generateResponseBasedOnTimestamp(senderID);
            } else {
                return formatHttpResponse("500 Internal Server Error", null);
            }
        } else {
            return formatHttpResponse("400 Bad Request", null);
        }
    }

    private boolean isValidSender(String senderID) {
        return senderID != null && !senderID.isEmpty();
    }

    private boolean isNewOrDelayedRequest(Long lastTimestamp, long currentTimestamp) {
        return lastTimestamp == null || (currentTimestamp - lastTimestamp) > THRESHOLD;
    }

    /**
     * Adds weather data to the server's data store.
     *
     * @param content The String representation of the weather data.
     * @param lamportTime The Lamport timestamp associated with the data.
     * @param senderID The identifier of the server sending the data.
     * @return True if the data was added successfully, false otherwise.
     */

    public boolean processWeatherData(String content, int lamportTime, String senderID) {
        try {
            JsonObject weatherDataJSON = JsonHandler.parseJSONObject(content);
            String stationId = extractStationId(weatherDataJSON);

            if (!isValidStation(stationId)) {
                return false;
            }

            WeatherData newWeatherData = new WeatherData(weatherDataJSON, lamportTime, senderID);
            dataStoreService.putData(stationId, newWeatherData);
            return true;
        } catch (JsonParseException e) {
            System.err.println("JSON Parsing Error: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidStation(String stationId) {
        return stationId != null && !stationId.isEmpty();
    }

    private String extractStationId(JsonObject weatherDataJSON) {
        return weatherDataJSON.has("id") ? weatherDataJSON.get("id").getAsString() : null;
    }

    private String generateResponseBasedOnTimestamp(String senderID) {
        long currentTimestamp = System.currentTimeMillis();
        Long lastTimestamp = dataStoreService.getTimestamp(senderID);

        // Update the timestamp for the senderID in DataStoreService
        dataStoreService.putTimestamp(senderID, currentTimestamp);

        if (isNewOrDelayedRequest(lastTimestamp, currentTimestamp)) {
            return formatHttpResponse("201 HTTP_CREATED", null);
        } else {
            return formatHttpResponse("200 OK", null);
        }
    }

    private String formatHttpResponse(String status, JsonObject jsonData) {
        StringBuilder response = new StringBuilder();
        lamportClock.tick();
        synchronizeWithSharedClock();

        response.append("HTTP/1.1 ").append(status).append("\r\n");
        response.append("LamportClock: ").append(lamportClock.getTime()).append("\r\n");

        if (jsonData != null) {
            String prettyData = JsonHandler.prettyPrint(jsonData);
            response.append("Content-Type: application/json\r\n");
            response.append("Content-Length: ").append(prettyData.length()).append("\r\n");
            response.append("\r\n");
            response.append(prettyData);
        } else {
            response.append("\r\n");
        }

        return response.toString();
    }

    /**
     * Main method to start the AggregationServer.
     * @param args Command line arguments, where the first argument is expected to be the server port.
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        NetworkHandler networkHandler = new SocketNetworkHandler();
        AggregationServer server = new AggregationServer(networkHandler);
        server.startAlone(port);
    }
}
