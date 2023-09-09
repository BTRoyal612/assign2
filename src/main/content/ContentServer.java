package main.content;

import org.json.JSONObject;
import main.common.LamportClock;
import main.common.JSONHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentServer {
    private LamportClock lamportClock = new LamportClock();
    private JSONObject weatherData;
    private ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    private NetworkHandler networkHandler;

    public ContentServer(NetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: ContentServer <serverName> <portNumber> <filePath>");
            return;
        }

        String serverName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String filePath = args[2];

        // Create an instance of SocketNetworkHandler for actual use.
        NetworkHandler networkHandler = new SocketNetworkHandler();
        ContentServer server = new ContentServer(networkHandler);
        server.loadWeatherData(filePath);
        server.uploadWeatherData(serverName, portNumber);
        server.sendHeartbeat(serverName, portNumber);

        // Add shutdown hook to gracefully terminate resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.heartbeatScheduler.shutdown();
            networkHandler.close();
        }));
    }

    public JSONObject getWeatherData() {
        return weatherData;
    }

    public void loadWeatherData(String filePath) {
        try {
            String fileContent = JSONHandler.readFile(filePath);
            weatherData = JSONHandler.convertTextToJSON(fileContent);
        } catch (Exception e) {
            System.out.println("Error loading weather data: " + e.getMessage());
        }
    }

    public String uploadWeatherData(String serverName, int portNumber) {
        try {
            JSONObject jsonData = new JSONObject(weatherData.toString()); // make a copy of the weather data
            jsonData.put("LamportClock", lamportClock.send());

            String putRequest = "PUT /uploadData HTTP/1.1\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + jsonData.toString().length() + "\r\n" +
                    "\r\n" +
                    jsonData;

            String response = networkHandler.sendData(serverName, portNumber, putRequest);

            if (response != null && (response.contains("200 OK") || response.contains("201 OK"))) {
                System.out.println("Data uploaded successfully.");
            } else {
                System.out.println("Error uploading data. Server response: " + response);
            }
            return response;
        } catch (Exception e) {
            System.out.println("Error while connecting to the server: " + e.getMessage());
            return null;
        }
    }

    public void sendHeartbeat(String serverName, int portNumber) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                lamportClock.tick();
                String heartbeatMessage = "HEARTBEAT " + lamportClock.getTime() + "\r\n";
                String response = networkHandler.sendData(serverName, portNumber, heartbeatMessage);

                if (response != null && (response.contains("200 OK") || response.contains("201 OK"))) {
                    System.out.println("Heartbeat acknowledged by server.");
                } else {
                    System.out.println("Error sending heartbeat. Server response: " + response);
                }
            } catch (Exception e) {
                System.out.println("Error sending heartbeat: " + e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);  // Initial delay 0, repeat every 15 seconds
    }
}