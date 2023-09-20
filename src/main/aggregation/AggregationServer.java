package main.aggregation;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final int TIMEOUT = 30000; // 30 seconds

    // Holds weather data. Key is content server ID, Value is the weather data in string format
    private ConcurrentHashMap<String, String> weatherDataMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> lastUpdateTimeMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port provided. Using default port: " + DEFAULT_PORT);
            }
        }

        new AggregationServer().startServer(port);
    }

    private void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClientRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClientRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            String[] requestParts = requestLine.split(" ");

            if (requestParts.length < 3) {
                out.println("HTTP/1.1 400 Bad Request");
                return;
            }

            String method = requestParts[0];
            switch (method) {
                case "GET":
                    // Return aggregated weather data
                    out.println("HTTP/1.1 200 OK");
                    out.println(weatherDataMap.toString());
                    break;

                case "PUT":
                    // Extract content server ID and weather data
                    String contentServerID = UUID.randomUUID().toString(); // Some mechanism to get unique ID
                    String data = in.readLine(); // Assuming one line contains the JSON-like data
                    if (data == null || data.isEmpty()) {
                        out.println("HTTP/1.1 204 No Content");
                    } else if (!isValidJSONLike(data)) {
                        out.println("HTTP/1.1 500 Internal Server Error");
                    } else {
                        weatherDataMap.put(contentServerID, data);
                        lastUpdateTimeMap.put(contentServerID, System.currentTimeMillis());
                        out.println(weatherDataMap.containsKey(contentServerID) ? "HTTP/1.1 200 OK" : "HTTP/1.1 201 Created");
                    }
                    break;

                default:
                    out.println("HTTP/1.1 400 Bad Request");
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Clean old data
        cleanOldData();
    }

    private boolean isValidJSONLike(String data) {
        // Your validation logic here, for example:
        return data.startsWith("{") && data.endsWith("}");
    }

    private void cleanOldData() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastUpdateTimeMap.entrySet()) {
            if (currentTime - entry.getValue() > TIMEOUT) {
                weatherDataMap.remove(entry.getKey());
                lastUpdateTimeMap.remove(entry.getKey());
            }
        }
    }
}
