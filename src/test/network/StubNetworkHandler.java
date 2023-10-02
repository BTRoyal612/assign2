package test.network;

import main.network.NetworkHandler;

import java.net.Socket;

public class StubNetworkHandler implements NetworkHandler {
    private String lastSentData;
    private int sentDataCount = 0;
    private String simulatedResponse = "200 OK"; // Default simulated response for sendData

    @Override
    public void startServer(int portNumber) {

    }

    @Override
    public Socket acceptConnection() {
        return null;
    }

    @Override
    public String waitForClientData(Socket clientSocket) {
        return null;
    }

    @Override
    public void sendResponseToClient(String response, Socket clientSocket) {

    }

    @Override
    public String sendAndReceiveData(String serverName, int portNumber, String data, boolean isContentServer) {
        this.lastSentData = data;
        sentDataCount++;
        return simulatedResponse;
    }

    @Override
    public void closeClient() {
    }

    @Override
    public void closeServer() {
    }

    @Override
    public int initializeSocket(String serverName, int portNumber) {
        return 0;
    }

    public String getLastSentData() {
        return lastSentData;
    }

    public void setSimulatedResponse(String response) {
        this.simulatedResponse = response;
    }

    public int getSentDataCount() {
        return sentDataCount;
    }
}
