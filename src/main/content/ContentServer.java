package main.content;

import com.google.gson.JsonObject;
import main.common.LamportClock;
import main.common.JSONHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentServer {
    private final String senderID;
    private LamportClock lamportClock = new LamportClock();
    private JsonObject weatherData;
    private ScheduledExecutorService dataUploadScheduler = Executors.newScheduledThreadPool(1);
    private NetworkHandler networkHandler;

    /**
     * Constructor for ContentServer.
     * Initializes the server with a unique ID and the provided network handler.
     * @param networkHandler The network handler to manage server-server communication.
     */
    public ContentServer(NetworkHandler networkHandler) {
        this.senderID = UUID.randomUUID().toString();
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
        server.initializeShutdownMonitor();

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
    public JsonObject getWeatherData() {
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
                // Step 1: Initialize the socket and get the Lamport clock value from the server
                int serverLamportClock = networkHandler.initializeSocket(serverName, portNumber);

                // Step 2: Set your Lamport clock using the value from the server
                lamportClock.setClock(serverLamportClock);

                String putRequest = "PUT /uploadData HTTP/1.1\r\n" +
                        "Host: " + serverName + "\r\n" +
                        "SenderID: " + senderID + "\r\n" +
                        "LamportClock: " + lamportClock.send() + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + weatherData.toString().length() + "\r\n" +
                        "\r\n" +
                        weatherData;

                String response = networkHandler.sendAndReceiveData(serverName, portNumber, putRequest, true);

                System.out.println(response);

                if (response != null && (response.contains("200") || response.contains("201"))) {
                    lamportClock.tick();
                    System.out.println("Data uploaded successfully.");
                } else {
                    System.out.println("Error uploading data. Server response: " + response);
                }
            } catch (Exception e) {
                System.out.println("Error while connecting to the server: " + e.getMessage());
                System.out.println("Retry in 15 second.");
                retryUpload(serverName, portNumber);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void retryUpload(String serverName, int portNumber) {
        try {
            // Sleep for 30 seconds before retrying
            Thread.sleep(15000);
            uploadWeatherData(serverName, portNumber);
        } catch (InterruptedException ie) {
            System.out.println("Retry interrupted: " + ie.getMessage());
        }
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

    private void shutdown() {
        System.out.println("Shutting down ContentServer...");

        // Shutdown data upload scheduler
        dataUploadScheduler.shutdown();
        try {
            if (!dataUploadScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                dataUploadScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            dataUploadScheduler.shutdownNow();
        }

        // Close any other resources such as the network handler
        networkHandler.closeClient();

        System.out.println("ContentServer shutdown complete.");
        System.exit(0);  // Exit the program
    }
}