package test.common;

import main.common.JsonHandler;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonHandlerTest {
    @Test
    void testTextToJsonConversion() {
        try {
            // Sample input text string.
            String content ="id: IDS60901\r\n" +
                    "name: Adelaide (West Terrace / ngayirdapira)\r\n" +
                    "air_temp: 13.3\r\n" +
                    "cloud: Partly cloudy";

            JsonObject result = JsonHandler.convertTextToJSON(content);

            // Assertions for the given fields.
            assertEquals("IDS60901", result.get("id").getAsString());
            assertEquals("Adelaide (West Terrace / ngayirdapira)", result.get("name").getAsString());
            assertEquals("13.3", result.get("air_temp").getAsString());
            assertEquals("Partly cloudy", result.get("cloud").getAsString());

        } catch (Exception e) {
            fail("Exception thrown during test: " + e.getMessage());
        }
    }

    @Test
    void testJSONToTextConversion() {
        try {
            JsonObject sampleJSON = new JsonObject();
            sampleJSON.addProperty("id", "IDS60901");
            sampleJSON.addProperty("air_temp", 13.3);
            sampleJSON.addProperty("cloud", "Partly cloudy");

            String result = JsonHandler.convertJSONToText(sampleJSON);
            assertTrue(result.contains("id: IDS60901"));
            assertTrue(result.contains("air_temp: 13.3"));
            assertTrue(result.contains("cloud: Partly cloudy"));

        } catch (Exception e) {
            fail("Exception thrown during test: " + e.getMessage());
        }
    }

    @Test
    public void testExtractJSONContent() {
        // Test with valid input
        String input = "Some random data before the JSON content. {\"key\":\"value\"} Some data after.";
        String expectedOutput = "{\"key\":\"value\"}";
        assertEquals(expectedOutput, JsonHandler.extractJSONContent(input));

        // Test with multiple JSON blocks (should only get the first one)
        input = "Random data {\"key1\":\"value1\"} more random data {\"key2\":\"value2\"} ending data.";
        expectedOutput = "{\"key1\":\"value1\"}";
        assertEquals(expectedOutput, JsonHandler.extractJSONContent(input));

        // Test with no JSON blocks
        input = "No JSON content here";
        assertNull(JsonHandler.extractJSONContent(input));
    }

    @Test
    public void testParseJSONObject() {
        // Test with valid JSON string
        String jsonString = "{\"key\":\"value\"}";
        JsonObject jsonObject = JsonHandler.parseJSONObject(jsonString);
        assertTrue(jsonObject.has("key"));
        assertEquals("value", jsonObject.get("key").getAsString());

        // Test with empty string
        jsonString = "";
        assertNull(JsonHandler.parseJSONObject(jsonString));

        // Test with null string
        jsonString = null;
        assertNull(JsonHandler.parseJSONObject(jsonString));

        // Test with invalid JSON string
        jsonString = "{key:\"value\"}";  // missing double quotes around key
        try {
            jsonObject = JsonHandler.parseJSONObject(jsonString);
        } catch (Exception e) {
            assertTrue(e instanceof com.google.gson.JsonParseException);
        }
    }

    @Test
    public void testPrettyPrint() {
        // Create a sample JsonObject
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", "IDS60901");
        jsonObject.addProperty("name", "Adelaide (West Terrace / ngayirdapira)");
        jsonObject.addProperty("state", "SA");
        jsonObject.addProperty("air_temp", 13.3);

        // Use the method to get the pretty printed version
        String pretty = JsonHandler.prettyPrint(jsonObject);

        // Create the expected pretty printed version
        String expected = "{\n" +
                "  \"id\": \"IDS60901\",\n" +
                "  \"name\": \"Adelaide (West Terrace / ngayirdapira)\",\n" +
                "  \"state\": \"SA\",\n" +
                "  \"air_temp\": 13.3\n" +
                "}";

        // Assert they are equal
        assertEquals(expected, pretty);
    }
}
