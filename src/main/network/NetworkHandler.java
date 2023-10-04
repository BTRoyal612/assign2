package main.network;

import java.io.IOException;
import java.net.Socket;

public interface NetworkHandler {
    // For Aggregation Server
    void startServer(int portNumber);

    Socket acceptConnection() throws IOException; // We let it throw IOException now

    String waitForClientData(Socket clientSocket);

    void sendResponseToClient(String response, Socket clientSocket);

    void closeServer();

    // For Content Server and GETClient
    int initializeSocket(String serverName, int portNumber);

    String sendAndReceiveData(String serverName, int portNumber, String data, boolean isContentServer);

    void closeClient();
}
