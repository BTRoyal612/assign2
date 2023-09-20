package main.content;

import org.json.JSONObject;
import main.common.LamportClock;
import main.common.JSONHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class ContentServer {
    private LamportClock lamportClock = new LamportClock();
    private JSONObject weatherData;
    private ScheduledExecutorService dataUploadScheduler = Executors.newScheduledThreadPool(1);
    private NetworkHandler networkHandler;

    private String serverID;

    public ContentServer(NetworkHandler networkHandler) {
        this.serverID = UUID.randomUUID().toString();
        this.networkHandler = networkHandler;
    }

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
            networkHandler.close();
        }));
    }

    public JSONObject getWeatherData() {
        return weatherData;
    }

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

    public void uploadWeatherData(String serverName, int portNumber) {
        dataUploadScheduler.scheduleAtFixedRate(() -> {
            try {
                JSONObject jsonData = new JSONObject(weatherData.toString()); // assuming this directly gives a JSONObject
                jsonData.put("LamportClock", lamportClock.send());

                String putRequest = "PUT /uploadData HTTP/1.1\r\n" +
                        "Host: " + serverName + "\r\n" +
                        "ServerID: " + serverID + "\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + jsonData.toString().length() + "\r\n" +
                        "\r\n" +
                        jsonData;

                String response = networkHandler.sendData(serverName, portNumber, putRequest);

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