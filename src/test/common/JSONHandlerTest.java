package test.common;

import main.common.JSONHandler;
import org.json.JSONObject;
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

            JSONObject result = JSONHandler.convertTextToJSON(content);

            // Assertions for the given fields.
            assertEquals("IDS60901", result.getString("id"));
            assertEquals("Adelaide (West Terrace / ngayirdapira)", result.getString("name"));
            assertEquals(13.3, result.getDouble("air_temp"));
            assertEquals("Partly cloudy", result.getString("cloud"));

        } catch (Exception e) {
            fail("Exception thrown during test: " + e.getMessage());
        }
    }

    @Test
    void testJSONToTextConversion() {
        try {
            JSONObject sampleJSON = new JSONObject();
            sampleJSON.put("id", "IDS60901");
            sampleJSON.put("air_temp", 13.3);
            sampleJSON.put("cloud", "Partly cloudy");

            String result = JSONHandler.convertJSONToText(sampleJSON);
            assertTrue(result.contains("id: IDS60901"));
            assertTrue(result.contains("air_temp: 13.3"));
            assertTrue(result.contains("cloud: Partly cloudy"));

        } catch (Exception e) {
            fail("Exception thrown during test: " + e.getMessage());
        }
    }
}
