package main.common;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JSONHandler {

    public static JSONObject convertTextToJSON(String inputText) throws Exception {
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

    public static String convertJSONToText(JSONObject jsonObject) {
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
