package main.common;

import com.google.gson.*;

import java.lang.reflect.Type;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class JsonHandler {
    private static final Gson gson = new Gson();

    public static String readFile(String filePath) throws Exception {
        if (filePath == null) {
            throw new Exception("Error: filePath is null.");
        }

        StringBuilder content = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new Exception("Error reading the file: " + e.getMessage());
        }

        return content.toString();
    }

    public static JsonObject convertTextToJSON(String inputText) throws Exception {
        if (inputText == null) {
            throw new Exception("Input text is null.");
        }

        String[] lines = inputText.split("\n");
        Map<String, Object> dataMap = new HashMap<>();

        for (String line : lines) {
            String[] parts = line.split(":", 2);  // Split by the first occurrence of ':'

            if (parts.length != 2) {
                throw new Exception("Invalid line format: " + line);
            }

            String key = parts[0].trim();
            String value = parts[1].trim();

            dataMap.put(key, value);
        }

        return gson.toJsonTree(dataMap).getAsJsonObject();
    }

    public static String convertJSONToText(JsonObject jsonObject) throws Exception {
        if (jsonObject == null) {
            throw new Exception("Error: jsonObject is null.");
        }

        StringBuilder stringBuilder = new StringBuilder();

        for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement valueElement = entry.getValue();

            String valueStr;
            if (valueElement.isJsonPrimitive() && (valueElement.getAsJsonPrimitive().isNumber() || valueElement.getAsJsonPrimitive().isString())) {
                valueStr = valueElement.getAsString();
            } else {
                valueStr = gson.toJson(valueElement);  // Serialize to String for other types
            }

            stringBuilder.append(key).append(": ").append(valueStr).append("\n");
        }

        return stringBuilder.toString();
    }

    public static String extractJSONContent(String data) {
        int startIndex = data.indexOf("{");
        int endIndex = data.indexOf("}", startIndex);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return data.substring(startIndex, endIndex + 1);
        } else {
            // No valid JSON content found
            return null;
        }
    }

    public static JsonObject parseJSONObject(String jsonData) throws JsonParseException {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return null;
        }

        return gson.fromJson(jsonData, JsonObject.class);
    }

    // Serialize an object to JSON
    public static <T> String serializeObject(T object) {
        return gson.toJson(object);
    }

    // Deserialize a JSON string to an object
    public static <T> T deserializeObject(String jsonString, Type type) throws JsonSyntaxException {
        return gson.fromJson(jsonString, type);
    }
}
