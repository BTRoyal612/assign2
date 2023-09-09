package main.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketNetworkHandler implements NetworkHandler {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

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
        if (socket == null || socket.isClosed()) {
            socket = new Socket(serverName, portNumber);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
    }

    @Override
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
