package main.network;

public interface NetworkHandler {
    String sendData(String serverName, int portNumber, String data);
    String receiveData(String serverName, int portNumber, String request);
    void close();
}
