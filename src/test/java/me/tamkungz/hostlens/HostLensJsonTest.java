package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLensJsonTest {

    @Test
    void toJsonShouldEscapeStringsAndNullNonFiniteNumbers() {
        JsonSample sample = new JsonSample("a\"b\\c\n", Double.NaN, Float.POSITIVE_INFINITY);

        String json = HostLensJson.toJson(sample);

        assertEquals("{\"text\":\"a\\\"b\\\\c\\n\",\"nan\":null,\"infinite\":null}", json);
    }

    @Test
    void toJsonShouldWriteIterableAndMapValues() {
        Map<String, Object> value = Map.of(
                "items", List.of("cpu", "gpu"),
                "enabled", true,
                "count", 2
        );

        String json = HostLensJson.toJson(value);

        assertTrue(json.contains("\"items\":[\"cpu\",\"gpu\"]"));
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"count\":2"));
    }

    private record JsonSample(String text, double nan, float infinite) {
    }
}
