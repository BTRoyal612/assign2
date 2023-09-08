package main.content.network;

public interface NetworkHandler {
    String sendData(String serverName, int portNumber, String data);
    void close();
}
