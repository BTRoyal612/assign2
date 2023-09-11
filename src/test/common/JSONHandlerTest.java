package test.common;

import main.common.JSONHandler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JSONHandlerTest {
    @Test
    void testTextToJsonConversion() {
        try {
            // Sample input text string.
            String content =
                    "id: IDS60901\n" +
                            "name: Adelaide (West Terrace / ngayirdapira)\n" +
                            "air_temp: 13.3\n" +
                            "cloud: Partly cloudy";

            Map<String, Object> result = JSONHandler.convertTextToJSON(content);

            // Assertions for the given fields.
            assertEquals("IDS60901", result.get("id"));
            assertEquals("Adelaide (West Terrace / ngayirdapira)", result.get("name"));
            assertEquals("13.3", result.get("air_temp"));
            assertEquals("Partly cloudy", result.get("cloud"));

        } catch (Exception e) {
            fail("Exception thrown during test: " + e.getMessage());
        }
    }

    @Test
    void testJSONToTextConversion() {
        try {
            Map<String, Object> sampleJSON = new HashMap<>();
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

    @Test
    void testPrintJSON() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "IDS60901");
        data.put("air_temp", "13.3");
        data.put("cloud", "Partly cloudy");
        data.put("local_date_time_full", "20230715160000");

        String result = JSONHandler.printJSON(data);
        String expected = "{\"id\":\"IDS60901\",\"air_temp\":\"13.3\",\"cloud\":\"Partly cloudy\",\"local_date_time_full\":\"20230715160000\"}";

        assertEquals(expected, result);
    }

    @Test
    void testParseJSON() {
        String jsonData = "{\"id\":\"IDS60901\",\"air_temp\":\"13.3\",\"cloud\":\"Partly cloudy\",\"local_date_time_full\":\"20230715160000\"}";
        Map<String, Object> result = JSONHandler.parseJSON(jsonData);

        assertEquals("IDS60901", result.get("id"));
        assertEquals("13.3", result.get("air_temp"));
        assertEquals("Partly cloudy", result.get("cloud"));
        assertEquals("20230715160000", result.get("local_date_time_full"));
    }

    @Test
    void testParseJSONWithNewLines() {
        String jsonData = "{\n\"id\":\"IDS60901\",\n\"air_temp\":\"13.3\",\n\"cloud\":\"Partly cloudy\",\n\"local_date_time_full\":\"20230715160000\"\n}";
        Map<String, Object> result = JSONHandler.parseJSON(jsonData);

        assertEquals("IDS60901", result.get("id"));
        assertEquals("13.3", result.get("air_temp"));
        assertEquals("Partly cloudy", result.get("cloud"));
        assertEquals("20230715160000", result.get("local_date_time_full"));
    }
}
