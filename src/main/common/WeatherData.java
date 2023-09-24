package main.common;

import org.json.JSONObject;

public class WeatherData implements Comparable<WeatherData> {
    private JSONObject data;   // Weather data
    private int lamportTime;   // Lamport clock time
    private String senderID;   // ID of the server from which the data is received

    public WeatherData(JSONObject data, int lamportTime, String serverId) {
        this.data = data;
        this.lamportTime = lamportTime;
        this.senderID = serverId;
    }

    public JSONObject getData() {
        return data;
    }

    public int getLamportTime() {
        return lamportTime;
    }

    public String getSenderID() {
        return senderID;
    }

    @Override
    public int compareTo(WeatherData other) {
        return Integer.compare(this.lamportTime, other.lamportTime);
    }

    @Override
    public String toString() {
        // You can format this as you see fit. This is a basic example.
        return "LamportTime: " + lamportTime + ", ServerID: " + senderID + ", Data: " + data.toString();
    }
}
