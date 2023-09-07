package main.content;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import org.json.JSONObject;
import main.common.LamportClock;
import main.common.JSONHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentServer {
    private LamportClock lamportClock = new LamportClock();
    private JSONObject weatherData;

    private ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: ContentServer <serverName> <portNumber> <filePath>");
            return;
        }

        String serverName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String filePath = args[2];

        ContentServer server = new ContentServer();
        server.loadWeatherData(filePath);
        server.uploadWeatherData(serverName, portNumber, server.weatherData);
        server.sendHeartbeat(serverName, portNumber);
    }

    private void loadWeatherData(String filePath) {
        try {
            String fileContent = JSONHandler.readFile(filePath);
            weatherData = JSONHandler.convertTextToJSON(fileContent);
        } catch (Exception e) {
            System.out.println("Error loading weather data: " + e.getMessage());
        }
    }
    public void uploadWeatherData(String serverName, int portNumber, JSONObject jsonData) {
        try (Socket clientSocket = new Socket(serverName, portNumber);
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            jsonData.put("LamportClock", lamportClock.send());

            String putRequest = "PUT /uploadData HTTP/1.1\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + jsonData.toString().length() + "\r\n" +
                    "\r\n" +
                    jsonData.toString();

            out.println(putRequest);

            // Check response from the server (simplified for brevity)
            String response = in.readLine();
            if (response != null && response.contains("200 OK")) {
                System.out.println("Data uploaded successfully.");
            } else {
                System.out.println("Error uploading data. Server response: " + response);
            }

        } catch (Exception e) {
            System.out.println("Error while connecting to the server: " + e.getMessage());
        }
    }

    private void sendHeartbeat(String serverName, int portNumber) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try (Socket clientSocket = new Socket(serverName, portNumber);
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                lamportClock.tick();

                String heartbeatMessage = "HEARTBEAT " + lamportClock.getTime() + "\r\n";

                out.println(heartbeatMessage);
                String response = in.readLine();

                // Check response from the server (simplified for brevity)
                if (response != null && response.contains("200 OK")) {
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
