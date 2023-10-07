package main.common;

import com.google.gson.JsonObject;

public class WeatherData implements Comparable<WeatherData> {
    private int lamportTime;   // Lamport clock time
    private String senderID;   // ID of the server from which the data is received
    private JsonObject data;   // Weather data

    /**
     * Constructs a new WeatherData object.
     * @param data The weather data as a JSON object.
     * @param lamportTime The Lamport timestamp associated with the data.
     * @param senderID The unique identifier of the sender/server.
     */
    public WeatherData(JsonObject data, int lamportTime, String senderID) {
        this.data = data;
        this.lamportTime = lamportTime;
        this.senderID = senderID;
    }

    /**
     * Retrieves the weather data.
     * @return The weather data as a JSON object.
     */
    public JsonObject getData() {
        return data;
    }

    /**
     * Retrieves the Lamport timestamp associated with this data.
     * @return The Lamport timestamp.
     */
    public int getLamportTime() {
        return lamportTime;
    }

    /**
     * Retrieves the unique identifier of the sender/server providing this data.
     * @return The sender's/server's ID.
     */
    public String getSenderID() {
        return senderID;
    }

    /**
     * Compares this WeatherData object to another based on Lamport timestamps.
     * This method helps in determining the order of events in a distributed system.
     * @param other The other WeatherData object to be compared with.
     * @return A negative integer, zero, or a positive integer if this object is less
     * than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(WeatherData other) {
        return Integer.compare(this.lamportTime, other.lamportTime);
    }

    /**
     * Provides a string representation of the WeatherData object.
     * @return A string representation containing the Lamport timestamp, sender ID, and data.
     */
    @Override
    public String toString() {
        return "LamportTime: " + lamportTime + ", ServerID: " + senderID + ", Data: " + data.toString();
    }
}
