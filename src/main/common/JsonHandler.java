package main.common;

import com.google.gson.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.reflect.Type;

public class JsonHandler {
    private static final Gson gson = new Gson();

    /**
     * Constructs a new JsonHandler. This constructor is private to prevent instantiation.
     */
    private JsonHandler() {}

    /**
     * Reads the contents of a specified file and returns it as a String.
     * @param filePath The path of the file to be read.
     * @return A string containing the contents of the file.
     * @throws Exception If filePath is null or any error occurs while reading the file.
     */
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

    /**
     * Converts a textual representation into a JSON object.
     * The input text should be in the format "key: value" with each entry on a new line.
     * @param inputText The text to be converted.
     * @return A JsonObject representing the converted data.
     * @throws Exception If inputText is null or any error occurs during conversion.
     */
    public static JsonObject convertTextToJSON(String inputText) throws Exception {
        if (inputText == null) {
            throw new Exception("Input text is null.");
        }

        String[] lines = inputText.split("\n");
        Map<String, Object> dataMap = new LinkedHashMap<>();

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

    /**
     * Converts a JsonObject into a human-readable text format.
     * Each entry in the JSON will be represented as "key: value" on a new line.
     * @param jsonObject The JsonObject to be converted.
     * @return A string representing the content of the JsonObject.
     * @throws Exception If jsonObject is null or any error occurs during conversion.
     */
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

    /**
     * Extracts the JSON content from a given string data.
     * This method tries to find the first valid JSON substring within the data.
     * @param data The string data from which to extract JSON content.
     * @return The extracted JSON content as a string or null if no valid JSON content is found.
     */
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

    /**
     * Parses a JSON-formatted string into a JsonObject.
     * @param jsonData The string containing JSON-formatted data.
     * @return A JsonObject representation of the given data.
     * @throws JsonParseException If the parsing process encounters a problem.
     */
    public static JsonObject parseJSONObject(String jsonData) throws JsonParseException {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return null;
        }

        return gson.fromJson(jsonData, JsonObject.class);
    }

    /**
     * Serializes an object into a JSON-formatted string.
     * @param object The object to be serialized.
     * @return A string containing the JSON-formatted representation of the object.
     */
    public static <T> String serializeObject(T object) {
        return gson.toJson(object);
    }

    /**
     * Deserializes a JSON-formatted string into an object of a specified type.
     *
     * @param jsonString The string containing JSON-formatted data.
     * @param type The type into which the data should be deserialized.
     * @return An object of the specified type.
     * @throws JsonSyntaxException If deserialization fails or if jsonString is not a valid representation for the type.
     */
    public static <T> T deserializeObject(String jsonString, Type type) throws JsonSyntaxException {
        return gson.fromJson(jsonString, type);
    }

    /**
     * Converts a JsonObject into a prettified string representation.
     *
     * @param jsonObject The JsonObject to be prettified.
     * @return A prettified string representation of the JsonObject.
     */
    public static String prettyPrint(JsonObject jsonObject) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonObject);
    }
}
