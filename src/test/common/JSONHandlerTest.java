package test.common;

import main.common.JSONHandler;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JSONHandlerTest {
    @Test
    void testTextToJsonConversion() {
        try {
            // Sample input text string.
            String content =
                    """
                    id: IDS60901
                    name: Adelaide (West Terrace / ngayirdapira)
                    air_temp: 13.3
                    cloud: Partly cloudy""";

            JsonObject result = JSONHandler.convertTextToJSON(content);

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

            String result = JSONHandler.convertJSONToText(sampleJSON);
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
        assertEquals(expectedOutput, JSONHandler.extractJSONContent(input));

        // Test with multiple JSON blocks (should only get the first one)
        input = "Random data {\"key1\":\"value1\"} more random data {\"key2\":\"value2\"} ending data.";
        expectedOutput = "{\"key1\":\"value1\"}";
        assertEquals(expectedOutput, JSONHandler.extractJSONContent(input));

        // Test with no JSON blocks
        input = "No JSON content here";
        assertNull(JSONHandler.extractJSONContent(input));
    }

    @Test
    public void testParseJSONObject() {
        // Test with valid JSON string
        String jsonString = "{\"key\":\"value\"}";
        JsonObject jsonObject = JSONHandler.parseJSONObject(jsonString);
        assertTrue(jsonObject.has("key"));
        assertEquals("value", jsonObject.get("key").getAsString());

        // Test with empty string
        jsonString = "";
        assertNull(JSONHandler.parseJSONObject(jsonString));

        // Test with null string
        jsonString = null;
        assertNull(JSONHandler.parseJSONObject(jsonString));

        // Test with invalid JSON string
        jsonString = "{key:\"value\"}";  // missing double quotes around key
        try {
            jsonObject = JSONHandler.parseJSONObject(jsonString);
        } catch (Exception e) {
            assertTrue(e instanceof com.google.gson.JsonParseException);
        }
    }
}
