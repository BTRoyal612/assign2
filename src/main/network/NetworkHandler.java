package main.network;

import java.net.Socket;

public interface NetworkHandler {
    // Server-side methods
    void startServer(int portNumber);

    // New method to accept a client connection
    Socket acceptConnection();

    String waitForClientData(Socket clientSocket);

    void sendResponseToClient(String response, Socket clientSocket);

    // Client-side methods
    String sendData(String serverName, int portNumber, String data);
    String receiveData(String serverName, int portNumber, String request);

    void close();
}
