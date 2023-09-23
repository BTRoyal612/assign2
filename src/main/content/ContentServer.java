package main.content;

import org.json.JSONObject;
import main.common.LamportClock;
import main.common.JSONHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentServer {
    private final String serverID;
    private LamportClock lamportClock = new LamportClock();
    private JSONObject weatherData;
    private ScheduledExecutorService dataUploadScheduler = Executors.newScheduledThreadPool(1);
    private final NetworkHandler networkHandler;

    /**
     * Constructor for ContentServer.
     * Initializes the server with a unique ID and the provided network handler.
     * @param networkHandler The network handler to manage server-server communication.
     */
    public ContentServer(NetworkHandler networkHandler) {
        this.serverID = UUID.randomUUID().toString();
        this.networkHandler = networkHandler;
    }

    /**
     * Main entry point for the ContentServer.
     * @param args Command line arguments, where:
     *             - The first argument specifies the server name.
     *             - The second argument is the port number.
     *             - The third argument is the file path to load weather data from.
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: ContentServer <serverName> <portNumber> <filePath>");
            return;
        }

        String serverName = args[0];
        int portNumber;
        try {
            portNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid port number provided.");
            return;
        }
        String filePath = args[2];

        // Create an instance of SocketNetworkHandler for actual use.
        NetworkHandler networkHandler = new SocketNetworkHandler();
        ContentServer server = new ContentServer(networkHandler);

        if (!server.loadWeatherData(filePath)) {
            System.out.println("Error: Failed to load weather data from " + filePath);
            return;
        }

        server.uploadWeatherData(serverName, portNumber);

        // Add shutdown hook to gracefully terminate resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.dataUploadScheduler.shutdown();  // Assuming dataUploadScheduler is accessible here
            networkHandler.closeClient();
        }));
    }

    /**
     * Retrieves the weather data stored in the server.
     * @return A JSONObject containing the weather data.
     */
    public JSONObject getWeatherData() {
        return weatherData;
    }

    /**
     * Loads weather data from the given file path and converts it to a JSONObject.
     * @param filePath The path to the file containing weather data.
     * @return True if the weather data was loaded successfully, false otherwise.
     */
    public boolean loadWeatherData(String filePath) {
        try {
            String fileContent = JSONHandler.readFile(filePath);
            weatherData = JSONHandler.convertTextToJSON(fileContent);
            return true;
        } catch (Exception e) {
            System.out.println("Error loading weather data: " + e.getMessage());
            return false;
        }
    }

    /**
     * Schedules the upload of weather data to a specified server at regular intervals.
     * Constructs a PUT request, sends it to the specified server, and handles the server's response.
     * Updates the Lamport clock based on successful uploads.
     * @param serverName The name or address of the receiving server.
     * @param portNumber The port number on which the receiving server is listening.
     */
    public void uploadWeatherData(String serverName, int portNumber) {
        dataUploadScheduler.scheduleAtFixedRate(() -> {
            try {
                JSONObject jsonData = new JSONObject(weatherData.toString()); // assuming this directly gives a JSONObject

                String putRequest = "PUT /uploadData HTTP/1.1\r\n" +
                        "Host: " + serverName + "\r\n" +
                        "ServerID: " + serverID + "\r\n" +
                        "LamportClock: " + lamportClock.send() + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + jsonData.toString().length() + "\r\n" +
                        "\r\n" +
                        jsonData;

                String response = networkHandler.sendAndReceiveData(serverName, portNumber, putRequest);

                if (response != null && (response.contains("200 OK") || response.contains("201 OK"))) {
                    lamportClock.send();
                    System.out.println("Data uploaded successfully.");
                } else {
                    System.out.println("Error uploading data. Server response: " + response);
                }
            } catch (Exception e) {
                System.out.println("Error while connecting to the server: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
}