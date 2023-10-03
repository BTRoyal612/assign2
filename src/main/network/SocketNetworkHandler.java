package main.network;

import java.io.*;
import java.net.InetSocketAddress;
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
    public String sendAndReceiveData(String serverName, int portNumber, String data, boolean isContentServer) {
        try {
            out.println(data);  // Sending the request data

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            int contentLength = 0;
            boolean isHeader = true;

            // Read headers
            while (isHeader && (line = in.readLine()) != null) {
                if (line.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }

                responseBuilder.append(line).append("\r\n");

                // Blank line indicates end of headers and start of body
                if (line.isEmpty()) {
                    isHeader = false;
                }
            }

            // Read body
            if (!isContentServer && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                responseBuilder.append(bodyChars);
            }

            return responseBuilder.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (!isContentServer) {
                closeClient();
            }
        }
    }

    @Override
    public int initializeSocket(String serverName, int portNumber) {
        try {
            clientSocket = new Socket(serverName, portNumber);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Parse the Lamport clock value sent by the server immediately after the connection
            String clockLine = in.readLine();

            if (clockLine == null) {
                throw new IOException("Server closed the connection without sending LamportClock.");
            }

            if (clockLine.startsWith("LamportClock: ")) {
                return Integer.parseInt(clockLine.split(":")[1].trim());
            } else {
                throw new IOException("Expected LamportClock value from server but received: " + clockLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeClient(); // Close any resources if there's an error.
            throw new RuntimeException("Error initializing socket", e);
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

    @Override
    public Socket createConnection(InetSocketAddress address) {
        try {
            return new Socket(address.getAddress(), address.getPort());
        } catch (IOException e) {
            // Handle the exception here, for example, by logging it or rethrowing as a runtime exception
            e.printStackTrace(); // Logging the error for now
            return null; // You can return null or handle this differently based on your needs
        }
    }

    @Override
    public void sendData(Socket socket, String data) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(data);
        } catch (IOException e) {
            // Handle the exception here, for example, by logging it or rethrowing as a runtime exception
            e.printStackTrace(); // Logging the error for now
        }
    }

    @Override
    public String receiveData(Socket socket) {
        return waitForClientData(socket);
    }

}
