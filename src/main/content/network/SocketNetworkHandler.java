package main.content.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketNetworkHandler implements NetworkHandler {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public String sendData(String serverName, int portNumber, String data) {
        try {
            clientSocket = new Socket(serverName, portNumber);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            out.println(data);
            return in.readLine(); // assuming a response is expected for each send operation
        } catch (IOException e) {
            return "Error while sending data: " + e.getMessage();
        }
    }

    @Override
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            // Handle or log the exception
        }
    }
}
