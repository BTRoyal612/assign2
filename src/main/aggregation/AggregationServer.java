package main.aggregation;

import main.common.WeatherData;
import org.json.JSONObject;
import main.common.LamportClock;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private volatile boolean shutdown = false;
    private Thread acceptThread;
    private NetworkHandler networkHandler;

    // Lamport clock for the server
    private LamportClock lamportClock = new LamportClock();

    // To store JSON entries along with their sources
    private Map<String, PriorityQueue<WeatherData>> dataStore = new ConcurrentHashMap<>();

    private LinkedBlockingQueue<Socket> requestQueue = new LinkedBlockingQueue<>();

    // To store timestamps for each entry
    private Map<String, Long> timestampStore = new ConcurrentHashMap<>();

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
    }

    /**
     * Starts the server and initializes required components.
     * @param portNumber The port number on which the server will listen for incoming connections.
     */
    public void start(int portNumber) {
        System.out.println("server start");
        networkHandler.startServer(portNumber); // Starting the server socket

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
            return "400 Bad Request";
        }
    }

    /**
     * Processes a GET request and returns an appropriate response.
     * @param headers A map containing request headers.
     * @param content The content/body of the request.
     * @return A string representing the server's response.
     */
    public String handleGetRequest(Map<String, String> headers, String content) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);

        String stationId = headers.get("StationID");
        if (stationId == null || stationId.isEmpty()) {
            // If no station ID is provided, get the first station's data from the datastore
            Optional<String> optionalStationId = dataStore.keySet().stream().findFirst();
            if (optionalStationId.isPresent()) {
                stationId = optionalStationId.get();
            } else {
                return formatHttpResponse("404 Not Found", "Null StationID", null);
            }
        }

        PriorityQueue<WeatherData> weatherDataQueue = dataStore.get(stationId);
        if (weatherDataQueue == null || weatherDataQueue.isEmpty()) {
            return formatHttpResponse("404 Not Found", null, null); // No data available for the given station ID
        }

        // Find the first WeatherData with Lamport time less than the request's Lamport time
        Optional<WeatherData> targetData = weatherDataQueue.stream()
                .filter(data -> data.getLamportTime() <= lamportTime)
                .findFirst();

        if (!targetData.isPresent()) {
            return formatHttpResponse("404 Not Found", "No Valid Data", null); // No data available matching the Lamport time condition
        }

        return formatHttpResponse("200 OK", null, targetData.get().getData().toString());
    }

    /**
     * Processes a PUT request and returns an appropriate response.
     * @param headers A map containing request headers.
     * @param content The content/body of the request.
     * @return A string representing the server's response.
     */
    private String handlePutRequest(Map<String, String> headers, String content) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);

        // Extract the server ID
        String serverId = headers.get("ServerID");
        if (serverId == null || serverId.isEmpty()) {
            return formatHttpResponse("400 Bad Request", "No ServerID", null); // Server ID is mandatory in PUT request
        }

        // Parse the content into a JSONObject
        JSONObject weatherDataJSON;
        try {
            weatherDataJSON = new JSONObject(content);
        } catch (Exception e) {
            return formatHttpResponse("400 Bad Request", "Malformed JSON", null); // Malformed JSON data
        }

        // Use the new method to add weather data to the DataStore
        if (addWeatherData(weatherDataJSON, lamportTime, serverId)) {
            return formatHttpResponse("200 OK", null, null);
        } else {
            return formatHttpResponse("400 Bad Request", null, null); // Failed to add data
        }
    }

    /**
     * Adds weather data to the server's data store.
     * @param weatherDataJSON The JSON representation of the weather data.
     * @param lamportTime The Lamport timestamp associated with the data.
     * @param serverId The identifier of the server sending the data.
     * @return True if the data was added successfully, false otherwise.
     */
    public boolean addWeatherData(JSONObject weatherDataJSON, int lamportTime, String serverId) {
        // Extract station ID from the parsed JSON
        String stationId = weatherDataJSON.optString("id", null);
        if (stationId == null || stationId.isEmpty()) {
            return false;  // Station ID missing in the JSON content
        }

        // Create a new WeatherData object
        WeatherData newData = new WeatherData(weatherDataJSON, lamportTime, serverId);

        // Add the new WeatherData object to the priority queue associated with the station ID
        dataStore.computeIfAbsent(stationId, k -> new PriorityQueue<>()).add(newData);

        return true;
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

        System.out.println("Shutting down server...");
    }

    // TODO: Implement methods to handle file management

    // TODO: Implement methods to remove stale entries
}
