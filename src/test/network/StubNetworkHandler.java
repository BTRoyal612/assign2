package test.network;

import main.network.NetworkHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class StubNetworkHandler implements NetworkHandler {
    private String lastSentData;
    private int sentDataCount = 0;
    private String simulatedResponse = "200 OK"; // Default simulated response for sendData

    @Override
    public void startServer(int portNumber) {
        // Do nothing in the stub
    }

    @Override
    public Socket acceptConnection() {
        return new Socket();  // Return a new mock socket without binding it
    }

    @Override
    public String waitForClientData(Socket clientSocket) {
        return "Simulated client data";  // Mocked data to simulate a client sending something
    }

    @Override
    public void sendResponseToClient(String response, Socket clientSocket) {
        // In this stub, we won't actually send anything over a network
        // But, we can save the response to check it later if needed
        lastSentData = response;
        sentDataCount++;
    }

    @Override
    public String sendAndReceiveData(String serverName, int portNumber, String data, boolean isContentServer) {
        lastSentData = data;
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
        return 0;  // Return a mocked value indicating success
    }

    @Override
    public Socket createConnection(InetSocketAddress serverAddress) {
        return new Socket();  // Return a new mock socket without binding it
    }

    @Override
    public void sendData(Socket serverSocket, String requestData) {
        // For the stub, just store the data to check later
        lastSentData = requestData;
        sentDataCount++;
    }

    @Override
    public String receiveData(Socket serverSocket) {
        return simulatedResponse;
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
