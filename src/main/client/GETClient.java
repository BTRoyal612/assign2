package main.client;

import main.common.JsonHandler;
import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import main.common.LamportClock;  // Importing the LamportClock class
import java.util.UUID;

public class GETClient {
    private final NetworkHandler networkHandler;
    private final String senderID;
    private LamportClock lamportClock = new LamportClock();

    /**
     * Constructor for GETClient.
     * Initializes the client with the given NetworkHandler and a randomly generated senderID.
     * @param networkHandler The network handler to manage client-server communication.
     */
    public GETClient(NetworkHandler networkHandler) {
        this.senderID = UUID.randomUUID().toString();
        this.networkHandler = networkHandler;
    }

    /**
     * Main entry point for the GETClient.
     * @param args Command line arguments, where the first argument specifies the server (format: <serverName>:<portNumber>),
     *             and the optional second argument is the stationID.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: GETClient <serverName>:<portNumber> <stationID>");
            return;
        }

        String[] serverInfo = args[0].split(":");
        String serverName = serverInfo[0];
        int portNumber = Integer.parseInt(serverInfo[1]);

        String stationID = args.length == 3 ? args[2] : null;

        NetworkHandler networkHandler = new SocketNetworkHandler();
        GETClient client = new GETClient(networkHandler);
        JsonObject response = client.getData(serverName, portNumber, stationID);

        // Interpret and print the response
        client.interpretResponse(response);

        networkHandler.closeClient();
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
            for (String line : lines) {
                System.out.println(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while converting JSON to text.", e);
        }
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
        // Step 1: Initialize the socket and get the Lamport clock value from the server
        int serverLamportClock = networkHandler.initializeSocket(serverName, portNumber);

        // Step 2: Set your Lamport clock using the value from the server
        lamportClock.setClock(serverLamportClock);

        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "SenderID: " + senderID + "\r\n" +
                "LamportClock: " + lamportClock.send() + "\r\n" +
                (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                "\r\n";

        try {
            String responseStr = networkHandler.sendAndReceiveData(serverName, portNumber, getRequest, false); // using the stubbed method

            System.out.println(responseStr);

            if (responseStr == null) {
                System.out.println("Error: No response received from the server.");
                return null;
            } else if (responseStr.contains("500")) {
                System.out.println("Internal Server Error: The JSON data on the server might be in incorrect format.");
                return null;
            } else if (responseStr.contains("204")) {
                System.out.println("Server response: No Content.");
                return null;
            }

            return JsonHandler.parseJSONObject(JsonHandler.extractJSONContent(responseStr));
        } catch (JsonParseException e) {
            System.out.println("Error parsing the server's JSON response: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
