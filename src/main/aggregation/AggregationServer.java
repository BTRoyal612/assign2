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
    private NetworkHandler networkHandler;

    // Lamport clock for the server
    private LamportClock lamportClock = new LamportClock();

    // To store JSON entries along with their sources
    private Map<String, PriorityQueue<WeatherData>> dataStore = new ConcurrentHashMap<>();

    private Queue<Socket> requestQueue = new LinkedList<>();

    // To store timestamps for each entry
    private Map<String, Long> timestampStore = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        NetworkHandler networkHandler = new SocketNetworkHandler();
        AggregationServer server = new AggregationServer(networkHandler);
        server.start(port);
    }

    public AggregationServer(NetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public void start(int portNumber) {
        networkHandler.startServer(portNumber); // Starting the server socket

        initializeShutdownMonitor(); // Start the shutdown monitor thread
        initializeAcceptThread();   // Start the client acceptance thread
        processClientRequests();    // Start processing client requests
    }

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

    private void initializeAcceptThread() {
        Thread acceptThread = new Thread(() -> {
            while (!shutdown) {
                Socket clientSocket = networkHandler.acceptConnection();
                synchronized (requestQueue) {
                    requestQueue.add(clientSocket);
                }
            }
        });
        acceptThread.start();
    }

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
            networkHandler.close();
        }
    }

    private Socket waitForClient() throws InterruptedException {
        synchronized (requestQueue) {
            while (requestQueue.isEmpty()) {
                Thread.sleep(10);
            }
            return requestQueue.poll();
        }
    }

    private void handleClientSocket(Socket clientSocket) {
        try {
            String requestData = networkHandler.waitForClientData(clientSocket);
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

    public void shutdown() {
        this.shutdown = true;
        System.out.println("Shutting down server...");
    }

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
                return "404 Not Found Null StationID";
            }
        }

        PriorityQueue<WeatherData> weatherDataQueue = dataStore.get(stationId);
        if (weatherDataQueue == null || weatherDataQueue.isEmpty()) {
            return "404 Not Found";  // No data available for the given station ID
        }

        // Find the first WeatherData with Lamport time less than the request's Lamport time
        Optional<WeatherData> targetData = weatherDataQueue.stream()
                .filter(data -> data.getLamportTime() <= lamportTime)
                .findFirst();

        if (targetData.isEmpty()) {
            return "404 Not Found No Valid Data";  // No data available matching the Lamport time condition
        }

        return "Station ID: " + stationId + "\n" + targetData.get();
    }


    private String handlePutRequest(Map<String, String> headers, String content) {
        int lamportTime = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        lamportClock.receive(lamportTime);

        // Extract the server ID
        String serverId = headers.get("ServerID");
        if (serverId == null || serverId.isEmpty()) {
            return "400 Bad Request No ServerID"; // Server ID is mandatory in PUT request
        }

        // Parse the content into a JSONObject
        JSONObject weatherDataJSON;
        try {
            weatherDataJSON = new JSONObject(content);
        } catch (Exception e) {
            return "400 Bad Request Malformed JSON";  // Malformed JSON data
        }

        // Use the new method to add weather data to the DataStore
        if (addWeatherData(weatherDataJSON, lamportTime, serverId)) {
            return "200 OK";
        } else {
            return "400 Bad Request"; // Failed to add data
        }
    }

    // TODO: Implement methods to handle file management

    // TODO: Implement methods to remove stale entries
}
