package main.common;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class JSONHandler {

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

    public static JSONObject convertTextToJSON(String inputText) throws Exception {
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

        return new JSONObject(dataMap);
    }

    public static String convertJSONToText(JSONObject jsonObject) throws Exception {
        if (jsonObject == null) {
            throw new Exception("Error: jsonObject is null.");
        }

        StringBuilder stringBuilder = new StringBuilder();

        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.get(key);  // Get value as Object

            // Convert the value object to string appropriately
            String valueStr;
            if (value instanceof Double || value instanceof Integer) {
                valueStr = String.valueOf(value);  // Convert numeric values directly
            } else {
                valueStr = jsonObject.getString(key);  // For other types, use getString
            }

            stringBuilder.append(key).append(": ").append(valueStr).append("\n");
        }

        return stringBuilder.toString();
    }
}
