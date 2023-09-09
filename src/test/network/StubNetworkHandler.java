package test.network;

import main.network.NetworkHandler;

public class StubNetworkHandler implements NetworkHandler {
    private String lastSentData;
    private String lastReceivedRequest;
    private int sentDataCount = 0;
    private String simulatedSendResponse = "200 OK"; // Default simulated response for sendData
    private String simulatedReceiveResponse = "200 OK"; // Default simulated response for receiveData

    @Override
    public String sendData(String serverName, int portNumber, String data) {
        this.lastSentData = data;
        sentDataCount++;
        return simulatedSendResponse;
    }

    @Override
    public String receiveData(String serverName, int portNumber, String request) {
        this.lastReceivedRequest = request;
        return simulatedReceiveResponse;
    }

    @Override
    public void close() {
        // In a stub, the close method might not have much to do.
        // You can optionally add some behavior here if needed for testing.
    }

    public String getLastSentData() {
        return lastSentData;
    }

    public String getLastReceivedRequest() {
        return lastReceivedRequest;
    }

    public void setSimulatedSendResponse(String response) {
        this.simulatedSendResponse = response;
    }

    public void setSimulatedReceiveResponse(String response) {
        this.simulatedReceiveResponse = response;
    }

    public int getSentDataCount() {
        return sentDataCount;
    }
}
