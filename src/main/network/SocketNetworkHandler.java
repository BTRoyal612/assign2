package main.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SocketNetworkHandler implements NetworkHandler {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public void startServer(int portNumber) {
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // New method to accept a client connection
    @Override
    public Socket acceptConnection() throws IOException {
        if (serverSocket == null) {
            throw new IllegalStateException("Server not started");
        }

        serverSocket.setSoTimeout(1000); // Set a timeout for the accept method (1 second for this example)
        try {
            return serverSocket.accept();
        } catch (SocketTimeoutException e) {
            // If timeout occurs, check if it's interrupted, else continue waiting
            if(Thread.currentThread().isInterrupted()){
                throw new IOException("Socket accept interrupted", e);
            }
            return null;
        }
    }

    @Override
    public String waitForClientData(Socket clientSocket) {
        StringBuilder requestBuilder = new StringBuilder();

        try {
            InputStream input = clientSocket.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input));

            String line;
            int contentLength = 0;
            boolean isHeader = true;

            // Read headers
            while (isHeader && (line = in.readLine()) != null) {
                if (line.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }

                requestBuilder.append(line).append("\r\n");

                // Blank line indicates end of headers and start of body
                if (line.isEmpty()) {
                    isHeader = false;
                }
            }

            // Read body
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                requestBuilder.append(bodyChars);
            }

            return requestBuilder.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void sendResponseToClient(String response, Socket clientSocket) { // Modified
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String sendAndReceiveData(String serverName, int portNumber, String data) {
        try {
            initializeSocket(serverName, portNumber);
            System.out.println("connect to server");
            out.println(data);

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
            closeClient();
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
    public void closeClient() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void closeServer() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
