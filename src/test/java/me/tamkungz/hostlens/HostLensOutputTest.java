package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLensOutputTest {

    @Test
    void snapshotShouldRenderPrettyString() {
        HostSnapshot snapshot = HostLens.quickCapture();

        String pretty = assertDoesNotThrow(snapshot::toPrettyString);

        assertNotNull(pretty);
        assertFalse(pretty.isBlank());
        assertTrue(pretty.contains("HostLens Snapshot"));
        assertTrue(pretty.contains("[Operating System]"));
        assertTrue(pretty.contains("[Runtime]"));
    }

    @Test
    void snapshotShouldRenderJsonWithTopLevelKeys() {
        HostSnapshot snapshot = HostLens.quickCapture();

        String json = assertDoesNotThrow(snapshot::toJson);

        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"capturedAt\""));
        assertTrue(json.contains("\"runtime\""));
        assertTrue(json.contains("\"operatingSystem\""));
        assertTrue(json.contains("\"gpus\""));
        assertTrue(json.contains("\"disks\""));
        assertTrue(json.contains("\"networks\""));
        assertTrue(json.contains("\"errors\""));
    }
}
