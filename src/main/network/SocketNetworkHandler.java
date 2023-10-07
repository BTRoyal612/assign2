package main.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class SocketNetworkHandler implements NetworkHandler {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    // For Aggregation Server and Load Balancer
    /**
     * Starts the server using a server socket on the specified port.
     * @param portNumber The port number where the server should listen for incoming connections.
     */
    @Override
    public void startServer(int portNumber) {
        closeServer();
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Listens for and accepts an incoming connection from a client.
     * Once a connection is established, it returns the socket associated with that client.
     * If the operation times out or is interrupted, it returns null.
     * @return The socket for the connected client or null if no connection was established.
     * @throws IOException If there's an issue with the network or server socket.
     */
    @Override
    public Socket acceptConnection() throws IOException {
        if (serverSocket == null || serverSocket.isClosed()) {
            throw new IllegalStateException("Server not started or already closed");
        }

        serverSocket.setSoTimeout(1000);
        try {
            return serverSocket.accept();
        } catch (SocketTimeoutException e) {
            // If timeout occurs, just return null
            if(Thread.currentThread().isInterrupted()){
                throw new IOException("Socket accept interrupted", e);
            }
            return null;
        } catch (SocketException e) {
            // Handle socket closed exception
            if ("Socket closed".equals(e.getMessage())) {
                System.out.println("Server socket was closed, no longer accepting connections.");
                return null;
            } else {
                throw e;
            }
        }
    }

    /**
     * Listens for incoming data from a connected client.
     * This method reads both the headers and the body of the HTTP request.
     * @param clientSocket The client's socket.
     * @return The data received from the client.
     */
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
                int bytesRead = 0;
                while (bytesRead < contentLength) {
                    int result = in.read(bodyChars, bytesRead, contentLength - bytesRead);
                    if (result == -1) {
                        break; // end of stream reached
                    }
                    bytesRead += result;
                }
                requestBuilder.append(bodyChars);
            }

            return requestBuilder.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends the specified response data to the connected client and then closes the associated resources.
     * @param response The data to be sent to the client.
     * @param clientSocket The client's socket.
     */
    @Override
    public void sendResponseToClient(String response, Socket clientSocket) { // Modified
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            out.close();
        }
    }

    /**
     * Gracefully shuts down the server by closing the server socket and any associated resources.
     */
    @Override
    public void closeServer() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // For Content Server and GETClient
    /**
     * Establishes a client connection with a specified server and initializes the associated socket.
     * Once connected, it expects to receive the Lamport clock value from the server.
     * @param serverName The name or address of the server to connect to.
     * @param portNumber The port number of the server.
     * @return The Lamport clock value sent by the server.
     */
    @Override
    public int initializeSocket(String serverName, int portNumber) {
        closeClient();

        try {
            clientSocket = new Socket(serverName, portNumber);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Parse the Lamport clock value sent by the server immediately after the connection
            // Read HTTP status line
            String clockLine = in.readLine();

            if (clockLine == null) {
                throw new IOException("Server closed the connection unexpectedly.");
            } else if (clockLine.startsWith("HTTP/1.1 503")) {
                throw new IOException("Received 503 Service Unavailable from the server.");
            } else if (clockLine.startsWith("LamportClock: ")) {
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

    /**
     * Sends the provided data to a server and then waits for its response.
     * After receiving the response, it shuts down the client and its associated resources.
     * @param serverName The name or address of the server.
     * @param portNumber The port number of the server.
     * @param data The data to be sent to the server.
     * @param isContentServer Flag to indicate if the caller is a content server.
     * @return The server's response.
     */
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
            closeClient();
        }
    }

    /**
     * Gracefully shuts down the client by closing its associated resources such as sockets, input and output streams.
     */
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
}
