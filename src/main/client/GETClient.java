package main.client;

import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;

import main.common.JsonHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import main.common.LamportClock;

import java.util.UUID;

public class GETClient {
    private final NetworkHandler networkHandler;
    private final String senderID;
    private LamportClock lamportClock;

    /**
     * Constructor for GETClient.
     * Initializes the client with the given NetworkHandler and a randomly generated senderID.
     * @param networkHandler The network handler to manage client-server communication.
     */
    public GETClient(NetworkHandler networkHandler) {
        this.senderID = UUID.randomUUID().toString();
        this.networkHandler = networkHandler;
        this.lamportClock = new LamportClock();
    }

    /**
     * Sends a GET request to retrieve weather data from the server.
     * Constructs a GET request, sends it to the specified server, and processes the response.
     * Updates the Lamport clock based on the server's response.
     * @param serverName The name or address of the server.
     * @param portNumber The port number on which the server is listening.
     * @param stationID Optional parameter specifying a specific stationID for data retrieval. Can be null.
     * @return A JSONObject containing the server's response or null in case of an error.
     */
    public JsonObject getData(String serverName, int portNumber, String stationID) {
        final int MAX_RETRIES = 3;
        int retries = 0;

        while (retries < MAX_RETRIES) {
            try {
                // Step 1: Initialize the socket and get the Lamport clock value from the server
                int serverLamportClock = networkHandler.initializeSocket(serverName, portNumber);

                // Step 2: Set your Lamport clock using the value from the server
                lamportClock.setClock(serverLamportClock);

                String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                        "User-Agent: ATOMClient/1/0\r\n" +
                        "SenderID: " + senderID + "\r\n" +
                        "LamportClock: " + lamportClock.send() + "\r\n" +
                        (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                        "\r\n";

                String response = networkHandler.sendAndReceiveData(serverName, portNumber, getRequest, false); // using the stubbed method
                System.out.println(response);
                System.out.println();

                if (response == null) {
                    System.out.println("Error: No response received from the server.");
                    System.out.println();
                    return null;
                } else if (response.startsWith("HTTP/1.1 204")) {
                    System.out.println("Server response: No Content.");
                    System.out.println();
                    return null;
                } else if (response.startsWith("HTTP/1.1 503")) {
                    System.out.println("Server response: Service Unavailable.");
                    System.out.println();
                    return null;
                }

                return JsonHandler.parseJSONObject(JsonHandler.extractJSONContent(response));
            } catch (JsonParseException e) {
                System.out.println("Error parsing the server's JSON response: " + e.getMessage());
                return null;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                if (++retries < MAX_RETRIES) {
                    System.out.println("Retry in 15 seconds...");
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException ie) {
                        System.out.println("Retry interrupted: " + ie.getMessage());
                    }
                } else {
                    System.out.println("Max retries reached.");
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Interprets and prints the response received from the server.
     * Converts the response JSON to text and prints it to the console.
     * @param response The JSONObject representing the server's response.
     */
    public void interpretResponse(JsonObject response) {
        if (response == null) {
            return;
        }

        try {
            String weatherDataText = JsonHandler.convertJSONToText(response);
            String[] lines = weatherDataText.split("\n");
            System.out.println();
            for (String line : lines) {
                System.out.println(line);
                Thread.sleep(500);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while converting JSON to text.", e);
        }
    }

    /**
     * Parses the provided server information to separate server name and port.
     * @param input The server information in the format "serverName:portNumber".
     * @return A string array where the first element is the server name and the second is the port number.
     */
    public static String[] parseServerInfo(String input) {
        // Remove the "http://" if present
        String strippedInput = input.replaceFirst("http://", "");

        // Split server name and domain from port number
        String[] parts = strippedInput.split(":");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid server format provided.");
        }

        // Further split the server part to extract just the server name
        String[] serverParts = parts[0].split("\\.");
        if (serverParts.length == 0) {
            throw new IllegalArgumentException("Invalid server name format provided.");
        }

        // Return only the server name (without domain) and the port number
        return new String[] {serverParts[0], parts[1]};
    }

    /**
     * Gracefully shuts down the GETClient, ensuring all resources are released.
     */
    public void shutdown() {
        System.out.println("Shutting down GETClient...");

        networkHandler.closeClient();

        System.out.println("GETClient shutdown complete.");
    }

    /**
     * Main execution point for GETClient.
     * Accepts command-line arguments specifying the server and optionally the stationID.
     * Fetches weather data from the specified server and prints the response.
     * @param args Command line arguments. The first argument specifies the server in the format "serverName:portNumber",
     *             and the optional second argument specifies the stationID.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: GETClient <serverName>:<portNumber> <stationID>");
            return;
        }

        String[] serverInfo = parseServerInfo(args[0]);
        String serverName = serverInfo[0];
        int portNumber = Integer.parseInt(serverInfo[1]);

        String stationID = args[1];

        NetworkHandler networkHandler = new SocketNetworkHandler();
        GETClient client = new GETClient(networkHandler);
        JsonObject response = client.getData(serverName, portNumber, stationID);

        // Interpret and print the response
        client.interpretResponse(response);

        networkHandler.closeClient();
    }
}
