package main.network;

import java.io.IOException;
import java.net.Socket;

public interface NetworkHandler {
    // Server-side methods
    void startServer(int portNumber);

    // New method to accept a client connection
    Socket acceptConnection() throws IOException; // We let it throw IOException now

    String waitForClientData(Socket clientSocket);

    void sendResponseToClient(String response, Socket clientSocket);

    String sendAndReceiveData(String serverName, int portNumber, String data, boolean isContentServer);

    void closeClient();

    void closeServer();
}
