package main.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import org.json.JSONObject;
import org.json.JSONTokener;
import main.common.LamportClock;  // Importing the LamportClock class

public class GETClient {

    private LamportClock lamportClock = new LamportClock();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: GETClient <serverName>:<portNumber> [<stationID>]");
            return;
        }

        String[] serverInfo = args[0].split(":");
        String serverName = serverInfo[0];
        int portNumber = Integer.parseInt(serverInfo[1]);

        String stationID = args.length == 3 ? args[2] : null;

        GETClient client = new GETClient();
        client.getData(serverName, portNumber, stationID);
    }

    public void getData(String serverName, int portNumber, String stationID) {
        try (Socket clientSocket = new Socket(serverName, portNumber);
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            int currentTime = lamportClock.send();

            String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                    "LamportClock: " + currentTime + "\r\n" +
                    (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                    "\r\n";

            out.println(getRequest);

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                response.append(line);
            }

            JSONObject jsonObject = new JSONObject(new JSONTokener(response.toString()));

            // Update Lamport clock based on the received response
            if (jsonObject.has("LamportClock")) {
                int receivedLamportClock = jsonObject.getInt("LamportClock");
                lamportClock.receive(receivedLamportClock);
            }

            for (String key : jsonObject.keySet()) {
                System.out.println(key + ": " + jsonObject.getString(key));
            }

        } catch (Exception e) {
            System.out.println("Error while connecting to the server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
