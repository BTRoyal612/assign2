package main.aggregation;

import main.common.WeatherData;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataStoreService {
    private Map<String, PriorityQueue<WeatherData>> dataStore = new ConcurrentHashMap<>();
    private Map<String, Long> timestampStore = new ConcurrentHashMap<>();

    public PriorityQueue<WeatherData> getData(String key) {
        return dataStore.get(key);
    }

    public void putData(String key, WeatherData value) {
        dataStore.computeIfAbsent(key, k -> new PriorityQueue<>()).add(value);
    }

    public Long getTimestamp(String key) {
        return timestampStore.get(key);
    }

    public void putTimestamp(String key, long value) {
        timestampStore.put(key, value);
    }

    public Set<String> getAllDataKeys() {
        return dataStore.keySet();
    }

    public Set<String> getAllTimestampKeys() {
        return timestampStore.keySet();
    }

    public void removeDataKey(String key) {
        dataStore.remove(key);
    }

    public void removeTimestampKey(String key) {
        timestampStore.remove(key);
    }

    public Map<String, PriorityQueue<WeatherData>> getDataMap() {
        return dataStore;
    }

    public Map<String, Long> getTimestampMap() {
        return timestampStore;
    }

    public void setDataMap(Map<String, PriorityQueue<WeatherData>> map) {
        this.dataStore = map;
    }

    public void setTimestampMap(Map<String, Long> map) {
        this.timestampStore = map;
    }
}
