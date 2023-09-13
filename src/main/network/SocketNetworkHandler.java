package main.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketNetworkHandler implements NetworkHandler {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public void startServer(int portNumber) {
        try {
            serverSocket = new ServerSocket(portNumber);
            clientSocket = serverSocket.accept(); // this will wait until a client connects

            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String waitForClientData() {
        try {
            return in.readLine(); // Reads a line of text sent by the client.
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void sendResponseToClient(String response) {
        out.println(response); // Sends a line of text to the client.
    }

    @Override
    public String sendData(String serverName, int portNumber, String data) {
        try {
            initializeSocket(serverName, portNumber);
            out.println(data);

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                response.append(line);
            }

            return response.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            close();
        }
    }

    @Override
    public String receiveData(String serverName, int portNumber, String request) {
        try {
            initializeSocket(serverName, portNumber);
            out.println(request);

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                response.append(line).append("\n");
            }

            return response.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            close();
        }
    }

    private void initializeSocket(String serverName, int portNumber) throws IOException {
        if (clientSocket == null || clientSocket.isClosed()) {
            clientSocket = new Socket(serverName, portNumber);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }
    }

    @Override
    public void close() {
        try {
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
