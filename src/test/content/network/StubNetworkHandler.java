package test.content.network;

import main.content.network.NetworkHandler;

public class StubNetworkHandler implements NetworkHandler {
    private String lastSentData;
    private int sentDataCount = 0;
    private String simulatedResponse = "200 OK"; // Default simulated response

   @Override
    public String sendData(String serverName, int portNumber, String data) {
        this.lastSentData = data;

        sentDataCount++;
        return simulatedResponse;
    }

    @Override
    public void close() {
        // In a stub, the close method might not have much to do.
        // You can optionally add some behavior here if needed for testing.
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
