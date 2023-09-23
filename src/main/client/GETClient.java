package main.client;

import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import org.json.JSONException;
import org.json.JSONObject;
import main.common.LamportClock;  // Importing the LamportClock class
import main.common.JSONHandler;
import java.util.UUID;


public class GETClient {
    private final NetworkHandler networkHandler;
    private final String serverID;
    private LamportClock lamportClock = new LamportClock();

    /**
     * Constructor for GETClient.
     * Initializes the client with the given NetworkHandler and a randomly generated serverID.
     * @param networkHandler The network handler to manage client-server communication.
     */
    public GETClient(NetworkHandler networkHandler) {
        this.serverID = UUID.randomUUID().toString();
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
        JSONObject response = client.getData(serverName, portNumber, stationID);

        // Interpret and print the response
        client.interpretResponse(response);

        networkHandler.closeClient();
    }

    /**
     * Interprets and prints the response received from the server.
     * Converts the response JSON to text and prints it to the console.
     * @param response The JSONObject representing the server's response.
     */
    public void interpretResponse(JSONObject response) {
        if (response == null) {
            System.out.println("Error: No response from server.");
            return;
        }

        try {
            String weatherDataText = JSONHandler.convertJSONToText(response);
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
    public JSONObject getData(String serverName, int portNumber, String stationID) {
        int currentTime = lamportClock.send();

        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "ServerID: " + serverID + "\r\n" +
                "LamportClock: " + currentTime + "\r\n" +
                (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                "\r\n";

        try {
            String responseStr = networkHandler.sendAndReceiveData(serverName, portNumber, getRequest, false); // using the stubbed method

            System.out.println(responseStr);

            if (responseStr.contains("500")) {
                System.out.println("Internal Server Error: The JSON data on the server might be in incorrect format.");
                return null;
            }

            JSONObject jsonObject = new JSONObject(JSONHandler.extractJSONContent(responseStr));

            if (jsonObject.has("LamportClock")) {
                int receivedLamportClock = jsonObject.getInt("LamportClock");
                lamportClock.receive(receivedLamportClock);
            }

            return jsonObject;
        } catch (JSONException e) {
            System.out.println("Error parsing the server's JSON response: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
