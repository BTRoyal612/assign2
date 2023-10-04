package main.aggregation;

import com.google.gson.reflect.TypeToken;
import main.common.JsonHandler;
import main.common.WeatherData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.lang.reflect.Type;

import java.util.concurrent.atomic.AtomicInteger;

public class DataStoreService {
    private static final long SAVE_INTERVAL_SECONDS = 60;
    private static final long CLEANUP_INTERVAL_SECONDS = 21;
    private static final long THRESHOLD = 40000;
    private static final String DATA_FILE_PATH = "src" + File.separator + "data" + File.separator + "dataStore.json";
    private static final String BACKUP_FILE_PATH = "src" + File.separator + "data" + File.separator + "dataStore_backup.json";
    private static final String TIMESTAMP_FILE_PATH = "src" + File.separator + "data" + File.separator + "timestampStore.json";
    private static final String TIMESTAMP_BACKUP_FILE_PATH = "src" + File.separator + "data" + File.separator + "timestampStore_backup.json";
    private static final AtomicInteger activeASCount = new AtomicInteger(0);
    private static volatile DataStoreService instance; // The single instance
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock shutdownLock = new ReentrantLock();
    private final ScheduledExecutorService fileSaveScheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);
    private Map<String, PriorityQueue<WeatherData>> dataStore = new ConcurrentHashMap<>();
    private Map<String, Long> timestampStore = new ConcurrentHashMap<>();

    // Constructor
    private DataStoreService() {
        if (instance != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
        loadDataFromFile();
        fileSaveScheduler.scheduleAtFixedRate(this::saveDataToFile, 0, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        cleanupScheduler.scheduleAtFixedRate(this::cleanupData, 0, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // Public method to get the instance
    public static DataStoreService getInstance() {
        if (instance == null) { // First check
            synchronized (DataStoreService.class) {
                if (instance == null) { // Double check
                    instance = new DataStoreService();
                }
            }
        }
        return instance;
    }

    public void saveDataToFile() {
        lock.lock();
        try {
            saveObjectToFile(getDataMap(), DATA_FILE_PATH, BACKUP_FILE_PATH);
            saveObjectToFile(getTimestampMap(), TIMESTAMP_FILE_PATH, TIMESTAMP_BACKUP_FILE_PATH);
        } finally {
            lock.unlock();
        }
    }

    private <T> void saveObjectToFile(T object, String filePath, String backupFilePath) {
        try {
            String jsonData = JsonHandler.serializeObject(object);
            Files.write(Paths.get(backupFilePath), jsonData.getBytes());
            Files.move(Paths.get(backupFilePath), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadDataFromFile() {
        lock.lock();
        try {
            Map<String, PriorityQueue<WeatherData>> loadedDataStore =
                    loadObjectFromFile(DATA_FILE_PATH, BACKUP_FILE_PATH, new TypeToken<ConcurrentHashMap<String, PriorityQueue<WeatherData>>>(){}.getType());

            Map<String, Long> loadedTimestampStore =
                    loadObjectFromFile(TIMESTAMP_FILE_PATH, TIMESTAMP_BACKUP_FILE_PATH, new TypeToken<ConcurrentHashMap<String, Long>>(){}.getType());

            setDataMap(loadedDataStore);
            setTimestampMap(loadedTimestampStore);
        } finally {
            lock.unlock();
        }
    }

    private <T> T loadObjectFromFile(String filePath, String backupFilePath, Type type) {
        try {
            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
            return JsonHandler.deserializeObject(jsonData, type);
        } catch (IOException e) {
            try {
                String backupData = new String(Files.readAllBytes(Paths.get(backupFilePath)));
                return JsonHandler.deserializeObject(backupData, type);
            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    public void cleanupData() {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            Set<String> staleSenderIds = getAllTimestampKeys().stream()
                    .filter(entry -> currentTime - getTimestamp(entry) > THRESHOLD)
                    .collect(Collectors.toSet());

            staleSenderIds.forEach(this::removeTimestampKey);

            if (getAllTimestampKeys().isEmpty()) {
                getAllDataKeys().forEach(this::removeDataKey);
                return;
            }

            for (String stationID : getAllDataKeys()) {
                PriorityQueue<WeatherData> queue = getData(stationID);
                queue.removeIf(weatherData -> staleSenderIds.contains(weatherData.getSenderID()));

                if (queue.isEmpty()) {
                    removeDataKey(stationID);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public PriorityQueue<WeatherData> getData(String key) {
        lock.lock();
        try {
            return dataStore.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void putData(String key, WeatherData value) {
        lock.lock();
        try {
            dataStore.computeIfAbsent(key, k -> new PriorityQueue<>()).add(value);
        } finally {
            lock.unlock();
        }
    }

    public Long getTimestamp(String key) {
        lock.lock();
        try {
            return timestampStore.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void putTimestamp(String key, long value) {
        lock.lock();
        try {
            timestampStore.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getAllDataKeys() {
        lock.lock();
        try {
            return dataStore.keySet();
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getAllTimestampKeys() {
        lock.lock();
        try {
            return timestampStore.keySet();
        } finally {
            lock.unlock();
        }
    }

    public void removeDataKey(String key) {
        lock.lock();
        try {
            dataStore.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void removeTimestampKey(String key) {
        lock.lock();
        try {
            timestampStore.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, PriorityQueue<WeatherData>> getDataMap() {
        lock.lock();
        try {
            return new ConcurrentHashMap<>(dataStore);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Long> getTimestampMap() {
        lock.lock();
        try {
            return new ConcurrentHashMap<>(timestampStore);
        } finally {
            lock.unlock();
        }
    }

    public void setDataMap(Map<String, PriorityQueue<WeatherData>> map) {
        lock.lock();
        try {
            this.dataStore = new ConcurrentHashMap<>(map);
        } finally {
            lock.unlock();
        }
    }

    public void setTimestampMap(Map<String, Long> map) {
        lock.lock();
        try {
            this.timestampStore = new ConcurrentHashMap<>(map);
        } finally {
            lock.unlock();
        }
    }

    public void registerAS() {
        activeASCount.incrementAndGet();
        // Additional logic if needed (e.g., resource initialization for the first AS)
    }

    public void deregisterAS() {
        shutdownLock.lock();
        try {
            if (activeASCount.decrementAndGet() == 0) {
                shutdown();
            }
        } finally {
            shutdownLock.unlock();
        }
    }

    public void shutdown() {
        System.out.println("Shutting down DataStoreService...");

        // 1. Save the data one last time
        saveDataToFile();

        // 2. Run cleanup operations one last time
        cleanupData();

        // 3. Shutdown the scheduled executors
        try {
            fileSaveScheduler.shutdown();
            fileSaveScheduler.awaitTermination(30, TimeUnit.SECONDS); // wait for ongoing tasks to finish, but max out at 30 seconds
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for fileSaveScheduler to terminate.");
            Thread.currentThread().interrupt();  // re-interrupt the thread
        } finally {
            if (!fileSaveScheduler.isTerminated()) {
                System.out.println("Forcing fileSaveScheduler to shutdown immediately.");
                fileSaveScheduler.shutdownNow();  // force shutdown if tasks didn't finish in time
            }
        }

        try {
            cleanupScheduler.shutdown();
            cleanupScheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for cleanupScheduler to terminate.");
            Thread.currentThread().interrupt();
        } finally {
            if (!cleanupScheduler.isTerminated()) {
                System.out.println("Forcing cleanupScheduler to shutdown immediately.");
                cleanupScheduler.shutdownNow();
            }
        }

        System.out.println("DataStoreService has been shut down.");
    }
}
