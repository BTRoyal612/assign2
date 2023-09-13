package main.network;

public interface NetworkHandler {
    // Server-side methods
    void startServer(int portNumber);
    String waitForClientData();
    void sendResponseToClient(String response);

    // Client-side methods
    String sendData(String serverName, int portNumber, String data);
    String receiveData(String serverName, int portNumber, String request);

    void close();
}
