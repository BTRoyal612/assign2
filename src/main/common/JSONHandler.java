package main.common;

import java.util.HashMap;
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

    public static Map<String, Object> convertTextToJSON(String inputText) throws Exception {
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

        System.out.println(dataMap);
        return dataMap;
    }

    public static String convertJSONToText(Map<String, Object> dataMap) throws Exception {
        if (dataMap == null) {
            throw new Exception("Error: dataMap is null.");
        }

        StringBuilder stringBuilder = new StringBuilder();

        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Convert the value object to string appropriately
            String valueStr;
            if (value instanceof Double || value instanceof Integer) {
                valueStr = String.valueOf(value);  // Convert numeric values directly
            } else {
                valueStr = (String) value;  // For other types, use toString
            }

            stringBuilder.append(key).append(": ").append(valueStr).append("\n");
        }

        return stringBuilder.toString();
    }

    public static String printJSON(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append("\"").append(entry.getValue()).append("\"");
            first = false;
        }

        sb.append("}");

        return sb.toString();
    }

    public static Map<String, Object> parseJSON(String jsonData) {
        Map<String, Object> resultMap = new HashMap<>();

        // Removing curly braces
        String trimmedData = jsonData.trim().substring(1, jsonData.length() - 1).trim();

        // Splitting by comma and newline, considering the comma or newline is not inside quotes
        String[] keyValuePairs = trimmedData.split(",|\\n(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String pair : keyValuePairs) {
            String[] parts = pair.split(":");
            if (parts.length >= 2) {
                String key = parts[0].trim().replace("\"", "");
                String value = parts[1].trim().replace("\"", "");
                resultMap.put(key, value);
            }
        }

        return resultMap;
    }
}
