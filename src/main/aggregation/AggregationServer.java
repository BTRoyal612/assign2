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
    private static AtomicInteger asCount = new AtomicInteger(0);
    private static DataStoreService dataStoreService = DataStoreService.getInstance();
    private volatile boolean shutdown;
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

    /**
     * Returns the port number on which the AggregationServer is running or is supposed to run.
     * @return the port number of the server.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Fetches the last piece of data that was received by the AggregationServer.
     * This can be particularly useful for testing purposes to verify if the
     * server received a specific piece of data.
     * @return a string representation of the last received data.
     */
    public String getLastReceivedData() {
        return lastReceivedData;
    }

    /**
     * Sets the value for the last piece of data that the AggregationServer received.
     * This method is marked as private because it is internal to the server's operation
     * and shouldn't be modified externally.
     * @param data the latest data received by the server.
     */
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

    /**
     * Synchronizes the local Lamport clock with a shared clock.
     * If the shared clock's time is ahead, the local Lamport clock is updated to match it.
     * After synchronization, the shared clock is then updated with the possibly incremented
     * value from the local Lamport clock. Finally, the local Lamport clock is ticked to increase its time.
     */
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
    }

    /**
     * Starts the server and initializes required components, for a load balancer.
     * @param portNumber The port number on which the server will listen for incoming connections.
     */
    public void start(int portNumber) {
        System.out.println("Started AggregationServer on port: " + portNumber);
        this.port = portNumber;
        this.shutdown = false;

        if (acceptThread != null && acceptThread.isAlive()) {
            throw new RuntimeException("Server is still running or hasn't been properly shut down");
        }

        networkHandler.startServer(portNumber); // Starting the server socket

        long startTime = System.currentTimeMillis();
        while (!isAlive() && (System.currentTimeMillis() - startTime) < 5000) {
            // Wait up to 5 seconds for server to be alive
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted while waiting for server to start");
            }
        }
        if (!isAlive()) {
            throw new RuntimeException("Server did not start successfully");
        }

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
            synchronizeWithSharedClock();
            String clockValue = "LamportClock: " + lamportClock.getTime();
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(clockValue);
            out.flush();

            lamportClock.tick();
            requestQueue.put(clientSocket);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initiates the server shutdown sequence, interrupting the client acceptance thread.
     */
    public void shutdown() {
        this.shutdown = true;

        // Interrupt the acceptThread to break the potential blocking call
        if(acceptThread != null) {
            acceptThread.interrupt();
            try {
                acceptThread.join(); // Ensure the thread is fully terminated
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Error while waiting for acceptThread to finish");
            }
        }

        networkHandler.closeServer();

        dataStoreService.deregisterAS();

        if (asCount.decrementAndGet() <= 0) {
            sharedClock.setClock(0);
            if (asCount.get() < 0) {
                asCount.set(0);
            }
        }

        System.out.println("Shutting down AggregationServer on port " + getPort());
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
     * Extracts the Lamport time from the given headers and synchronizes the local Lamport clock
     * with the extracted time. After synchronization, it retrieves and returns the updated Lamport clock's time.
     * @param headers Headers from which to extract the Lamport time.
     * @return Updated Lamport clock time.
     */
    private int getLamportTimeFromHeaders(Map<String, String> headers) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);
        synchronizeWithSharedClock();
        lamportClock.tick();
        return lamportClock.getTime();
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

    /**
     * Processes a GET request and returns an appropriate response.
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

    /**
     * Checks if the provided weather data queue is empty or null.
     * @param queue The queue to check.
     * @return True if the queue is null or empty, otherwise false.
     */
    private boolean isWeatherDataQueueEmpty(PriorityQueue<WeatherData> queue) {
        return queue == null || queue.isEmpty();
    }

    /**
     * Extracts the Station ID from the given headers or defaults to the first available
     * station ID from the datastore if not found in the headers.
     * @param headers Headers from which to extract the Station ID.
     * @return Extracted or default Station ID.
     */
    private String getStationIdFromHeadersOrDefault(Map<String, String> headers) {
        String stationId = headers.get("StationID");
        if (stationId != null && !stationId.isEmpty()) {
            return stationId;
        }
        return dataStoreService.getAllDataKeys().stream().findFirst().orElse(null);
    }

    /**
     * Retrieves the weather data for a given Lamport time from the provided queue.
     * The method finds the entry with the largest Lamport time that is less than or equal to the provided time.
     * @param queue Queue containing weather data.
     * @param lamportTime The Lamport time for which data is needed.
     * @return An optional containing the weather data if found, or empty otherwise.
     */
    private Optional<WeatherData> getWeatherDataForLamportTime(PriorityQueue<WeatherData> queue, int lamportTime) {
        return queue.stream()
                .filter(data -> data.getLamportTime() <= lamportTime)  // This line ensures the data is less than or equal to provided clock
                .max(Comparator.comparingInt(WeatherData::getLamportTime));  // This line finds the largest among those
    }

    /**
     * Processes a PUT request and returns an appropriate response.
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

    /**
     * Checks if the given sender ID is valid.
     * @param senderID The ID to validate.
     * @return True if the senderID is not null and not empty, otherwise false.
     */
    private boolean isValidSender(String senderID) {
        return senderID != null && !senderID.isEmpty();
    }

    /**
     * Determines if a request is either new or has been delayed beyond a threshold.
     *
     * @param lastTimestamp The timestamp of the last request.
     * @param currentTimestamp The timestamp of the current request.
     * @return True if the request is new or delayed beyond the threshold, otherwise false.
     */
    private boolean isNewOrDelayedRequest(Long lastTimestamp, long currentTimestamp) {
        return lastTimestamp == null || (currentTimestamp - lastTimestamp) > THRESHOLD;
    }

    /**
     * Adds weather data to the server's data store.
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

    /**
     * Validates if the provided station ID is neither null nor empty.
     * @param stationId The Station ID to validate.
     * @return True if the stationId is valid, otherwise false.
     */
    private boolean isValidStation(String stationId) {
        return stationId != null && !stationId.isEmpty();
    }

    /**
     * Extracts and returns the station ID from the provided JSON object representing weather data.
     * Returns null if the ID is not present in the JSON.
     * @param weatherDataJSON JSON object containing weather data.
     * @return Extracted station ID or null if not found.
     */
    private String extractStationId(JsonObject weatherDataJSON) {
        return weatherDataJSON.has("id") ? weatherDataJSON.get("id").getAsString() : null;
    }

    /**
     * Generates an HTTP response based on the difference between the current timestamp
     * and the last known timestamp for the given senderID. If the request is new or delayed,
     * it returns a "201 HTTP_CREATED" response; otherwise, it returns a "200 OK" response.
     * @param senderID The ID of the sender making the request.
     * @return HTTP response string.
     */
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

    /**
     * Formats the provided HTTP status and JSON data into a full HTTP response string.
     * This method also updates the Lamport clock and synchronizes it with the shared clock.
     * @param status The HTTP status code and message.
     * @param jsonData JSON data to be included in the response body.
     * @return Formatted HTTP response string.
     */
    private String formatHttpResponse(String status, JsonObject jsonData) {
        StringBuilder response = new StringBuilder();
        lamportClock.tick();
        synchronizeWithSharedClock();
        lamportClock.tick();

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
        server.start(port);
    }
}
