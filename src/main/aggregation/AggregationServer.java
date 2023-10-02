package main.aggregation;

import main.common.JSONHandler;
import main.common.WeatherData;
import com.google.gson.*;
import main.common.LamportClock;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Type;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final long THRESHOLD = 40000;
    private volatile boolean shutdown = false;
    private Thread acceptThread;
    private NetworkHandler networkHandler;
    private DataStoreService dataStoreService;

    private ScheduledExecutorService cleanupScheduler;

    // Lamport clock for the server
    private LamportClock lamportClock = new LamportClock();

    private LinkedBlockingQueue<Socket> requestQueue = new LinkedBlockingQueue<>();

    private static final String DATA_FILE_PATH = "src" + File.separator + "data" + File.separator + "dataStore.json";
    private static final String BACKUP_FILE_PATH = "src" + File.separator + "data" + File.separator + "dataStore_backup.json";
    private static final String TIMESTAMP_FILE_PATH = "src" + File.separator + "data" + File.separator + "timestampStore.json";
    private static final String TIMESTAMP_BACKUP_FILE_PATH = "src" + File.separator + "data" + File.separator + "timestampStore_backup.json";
    private ScheduledExecutorService fileSaveScheduler;

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

    /**
     * Constructor for AggregationServer.
     * @param networkHandler The network handler responsible for handling server's network interactions.
     */
    public AggregationServer(NetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
        this.dataStoreService = new DataStoreService();
    }

    /**
     * Starts the server and initializes required components.
     * @param portNumber The port number on which the server will listen for incoming connections.
     */
    public void start(int portNumber) {
        System.out.println("server start");
        networkHandler.startServer(portNumber); // Starting the server socket

        // Schedule periodic saving of data
        fileSaveScheduler = Executors.newScheduledThreadPool(1);
        fileSaveScheduler.scheduleAtFixedRate(this::saveDataToFile, 0, 60, TimeUnit.SECONDS); // for example, every minute
        loadDataFromFile();  // recover data at startup

        // Initialize cleanup task
        cleanupScheduler = Executors.newScheduledThreadPool(1);
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleEntries, 0, 21, TimeUnit.SECONDS);

        System.out.println("monitor shutdown start");
        initializeShutdownMonitor(); // Start the shutdown monitor thread

        System.out.println("accept connection start");
        initializeAcceptThread();   // Start the client acceptance thread

        System.out.println("process requests");
        processClientRequests();    // Start processing client requests
    }

    /**
     * Initializes a monitor thread that listens for SHUTDOWN command from the console to shut down the server.
     */
    private void initializeShutdownMonitor() {
        Thread monitorThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                if ("SHUTDOWN".equalsIgnoreCase(input)) {
                    shutdown();
                    break;
                }
            }
        });
        monitorThread.start();
    }

    // Atomic File Save method
    private synchronized void saveDataToFile() {
        saveObjectToFile(dataStoreService.getDataMap(), DATA_FILE_PATH, BACKUP_FILE_PATH);
        saveObjectToFile(dataStoreService.getTimestampMap(), TIMESTAMP_FILE_PATH, TIMESTAMP_BACKUP_FILE_PATH);
    }

    private synchronized <T> void saveObjectToFile(T object, String filePath, String backupFilePath) {
        try {
            // Convert the object to JSON
            String jsonData = JSONHandler.serializeObject(object);

            // Save to backup file
            Files.write(Paths.get(backupFilePath), jsonData.getBytes());

            // Atomically move backup file to main file
            Files.move(Paths.get(backupFilePath), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDataFromFile() {
        Map<String, PriorityQueue<WeatherData>> loadedDataStore =
                loadObjectFromFile(DATA_FILE_PATH, BACKUP_FILE_PATH, new TypeToken<ConcurrentHashMap<String, PriorityQueue<WeatherData>>>(){}.getType());

        Map<String, Long> loadedTimestampStore =
                loadObjectFromFile(TIMESTAMP_FILE_PATH, TIMESTAMP_BACKUP_FILE_PATH, new TypeToken<ConcurrentHashMap<String, Long>>(){}.getType());

        // Set loaded data to DataStoreService
        dataStoreService.setDataMap(loadedDataStore);
        dataStoreService.setTimestampMap(loadedTimestampStore);
    }

    private <T> T loadObjectFromFile(String filePath, String backupFilePath, Type type) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
            return JSONHandler.deserializeObject(jsonData, type);
        } catch (IOException e) {
            // If there's an error, try to load from the backup file
            try {
                String backupData = new String(Files.readAllBytes(Paths.get(backupFilePath)));
                return JSONHandler.deserializeObject(backupData, type);
            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    private void cleanupStaleEntries() {
        System.out.println("Before Cleanup:");
        printDataStoreAndTimestamps();
        long currentTime = System.currentTimeMillis();

        // Identify stale server IDs
        Set<String> staleSenderIds = dataStoreService.getAllTimestampKeys().stream()
                .filter(entry -> currentTime - dataStoreService.getTimestamp(entry) > THRESHOLD)
                .collect(Collectors.toSet());

        // Remove stale server IDs from timestampStore
        staleSenderIds.forEach(dataStoreService::removeTimestampKey);

        // If the timestampStore is empty, clear the entire dataStore
        if (dataStoreService.getAllTimestampKeys().isEmpty()) {
            dataStoreService.getAllDataKeys().forEach(dataStoreService::removeDataKey);
            return;
        }

        // Remove data entries associated with stale server IDs
        for (String stationID : dataStoreService.getAllDataKeys()) {
            PriorityQueue<WeatherData> queue = dataStoreService.getData(stationID);
            queue.removeIf(weatherData -> staleSenderIds.contains(weatherData.getSenderID()));

            if (queue.isEmpty()) {
                dataStoreService.removeDataKey(stationID);
            }
        }

        System.out.println("After Cleanup:");
        printDataStoreAndTimestamps();
    }

    private void printDataStoreAndTimestamps() {
        System.out.println("\nDataStore:");
        System.out.println(dataStoreService.getDataMap());

        System.out.println("\nTimestampStore:");
        System.out.println(dataStoreService.getTimestampMap());
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
                    System.out.println("new connection");
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
            return handleGetRequest(headers, content);
        } else if ("PUT".equalsIgnoreCase(requestType)) {
            return handlePutRequest(headers, content);
        } else {
            return formatHttpResponse("400 Bad Request", null, null);
        }
    }

    /**
     * Processes a GET request and returns an appropriate response.
     *
     * @param headers A map containing request headers.
     * @param content The content/body of the request.
     * @return A string representing the server's response.
     */
    public String handleGetRequest(Map<String, String> headers, String content) {
        int lamportTime = getLamportTimeFromHeaders(headers);

        // Retrieve station ID from headers or use default if not provided.
        String stationId = getStationIdFromHeadersOrDefault(headers);
        if (stationId == null) {
            return formatHttpResponse("204 No Content", null, null);
        }

        // Get weather data for the specified station ID.
        PriorityQueue<WeatherData> weatherDataQueue = dataStoreService.getData(stationId);
        if (isWeatherDataQueueEmpty(weatherDataQueue)) {
            return formatHttpResponse("204 No Content", null, null);
        }

        // Retrieve the first WeatherData with a Lamport time less than or equal to the request's Lamport time.
        Optional<WeatherData> targetData = getWeatherDataForLamportTime(weatherDataQueue, lamportTime);

        return targetData
                .map(weatherData -> formatHttpResponse("200 OK", null, weatherData.getData().toString()))
                .orElse(formatHttpResponse("204 No Content", null, null));
    }

    private int getLamportTimeFromHeaders(Map<String, String> headers) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);
        return lamportClock.getTime();
    }

    private String getStationIdFromHeadersOrDefault(Map<String, String> headers) {
        String stationId = headers.get("StationID");
        if (stationId != null && !stationId.isEmpty()) {
            return stationId;
        }
        return dataStoreService.getAllDataKeys().stream().findFirst().orElse(null);
    }

    private boolean isWeatherDataQueueEmpty(PriorityQueue<WeatherData> queue) {
        return queue == null || queue.isEmpty();
    }

    private Optional<WeatherData> getWeatherDataForLamportTime(PriorityQueue<WeatherData> queue, int lamportTime) {
        return queue.stream()
                .filter(data -> data.getLamportTime() <= lamportTime)
                .findFirst();
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
        if (isValidSender(senderID) && processWeatherData(content, lamportTime, senderID)) {
            return generateResponseBasedOnTimestamp(senderID);
        } else {
            return formatHttpResponse("400 Bad Request", null, null);
        }
    }

    private boolean isValidSender(String senderID) {
        return senderID != null && !senderID.isEmpty();
    }

    private boolean processWeatherData(String content, int lamportTime, String senderID) {
        try {
            JsonObject weatherDataJSON = JSONHandler.parseJSONObject(content);
            WeatherData newWeatherData = new WeatherData(weatherDataJSON, lamportTime, senderID);
            dataStoreService.putData(senderID, newWeatherData);
            return true;
        } catch (JsonParseException e) {
            System.err.println("JSON Parsing Error: " + e.getMessage());
            return false;
        }
    }

    private String generateResponseBasedOnTimestamp(String senderID) {
        long currentTimestamp = System.currentTimeMillis();
        Long lastTimestamp = dataStoreService.getTimestamp(senderID);

        // Update the timestamp for the senderID in DataStoreService
        dataStoreService.putTimestamp(senderID, currentTimestamp);

        if (isNewOrDelayedRequest(lastTimestamp, currentTimestamp)) {
            return formatHttpResponse("201 HTTP_CREATED", null, null);
        } else {
            return formatHttpResponse("200 OK", null, null);
        }
    }

    private boolean isNewOrDelayedRequest(Long lastTimestamp, long currentTimestamp) {
        return lastTimestamp == null || (currentTimestamp - lastTimestamp) > THRESHOLD;
    }

    /**
     * Adds weather data to the server's data store.
     *
     * @param weatherDataJSON The JSON representation of the weather data.
     * @param lamportTime The Lamport timestamp associated with the data.
     * @param senderID The identifier of the server sending the data.
     * @return True if the data was added successfully, false otherwise.
     */
    public boolean addWeatherData(JsonObject weatherDataJSON, int lamportTime, String senderID) {
        String stationId = extractStationId(weatherDataJSON);

        if (!isValidStation(stationId)) {
            return false;
        }

        dataStoreService.putData(stationId, new WeatherData(weatherDataJSON, lamportTime, senderID));
        return true;
    }

    private String extractStationId(JsonObject weatherDataJSON) {
        return weatherDataJSON.has("id") ? weatherDataJSON.get("id").getAsString() : null;
    }

    private boolean isValidStation(String stationId) {
        return stationId != null && !stationId.isEmpty();
    }

    private String formatHttpResponse(String status, String message, String jsonData) {
        StringBuilder response = new StringBuilder();

        response.append("HTTP/1.1 ").append(status).append("\r\n");
        response.append("LamportClock: ").append(lamportClock.send()).append("\r\n");

        if (jsonData != null) {
            response.append("Content-Type: application/json\r\n");
            response.append("Content-Length: ").append(jsonData.length()).append("\r\n");
            response.append("\r\n");
            response.append(jsonData);
        } else if (message != null) {
            response.append("Content-Length: ").append(message.length()).append("\r\n");
            response.append("\r\n");
            response.append(message);
        } else {
            response.append("\r\n");
        }

        return response.toString();
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

        // Shutdown fileSaveScheduler gracefully
        fileSaveScheduler.shutdown();
        try {
            if (!fileSaveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                fileSaveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            fileSaveScheduler.shutdownNow();
        }

        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Shutting down server...");
    }

    // TODO: Implement methods to handle file management
}
