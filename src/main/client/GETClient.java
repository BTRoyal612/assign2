package main.client;

import main.network.NetworkHandler;
import main.network.SocketNetworkHandler;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import main.common.LamportClock;  // Importing the LamportClock class
import main.common.JSONHandler;

public class GETClient {
    private final NetworkHandler networkHandler;
    private LamportClock lamportClock = new LamportClock();

    // Constructor that accepts a NetworkHandler, defaults to real implementation if none provided.
    public GETClient(NetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: GETClient <serverName>:<portNumber> [<stationID>]");
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
    }

    public void interpretResponse(JSONObject response) {
        if (response == null) {
            System.out.println("Error: No response from server.");
            return;
        }

        if (!response.has("status")) {
            System.out.println("Error: Invalid response format.");
            return;
        }

        String status = response.getString("status");

        if ("not available".equals(status)) {
            System.out.println("No weather data available.");
        } else if ("available".equals(status)) {
            try {
                String weatherDataText = JSONHandler.convertJSONToText(response);
                String[] lines = weatherDataText.split("\n");
                for (String line : lines) {
                    System.out.println(line);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error while converting JSON to text.", e);
            }
        } else {
            System.out.println("Error: Unknown response status: " + status);
        }
    }

    public JSONObject getData(String serverName, int portNumber, String stationID) {
        int currentTime = lamportClock.send();

        String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                "LamportClock: " + currentTime + "\r\n" +
                (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                "\r\n";

        try {
            String responseStr = networkHandler.receiveData(serverName, portNumber, getRequest); // using the stubbed method

            if (responseStr.startsWith("500")) {
                System.out.println("Internal Server Error: The JSON data on the server might be in incorrect format.");
                return null;
            }

            JSONObject jsonObject = new JSONObject(new JSONTokener(responseStr));

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
