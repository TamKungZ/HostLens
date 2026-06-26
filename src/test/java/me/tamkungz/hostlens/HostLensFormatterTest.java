package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostLensFormatterTest {

    @Test
    void formatBytesShouldHandleUnknownAndSmallValues() {
        assertEquals("unknown", HostLensFormatter.formatBytes(-1));
        assertEquals("0 B", HostLensFormatter.formatBytes(0));
        assertEquals("1 B", HostLensFormatter.formatBytes(1));
        assertEquals("1023 B", HostLensFormatter.formatBytes(1023));
    }

    @Test
    void formatBytesShouldUseBinaryUnits() {
        assertEquals("1.00 KB", HostLensFormatter.formatBytes(1024));
        assertEquals("1.00 MB", HostLensFormatter.formatBytes(1024L * 1024L));
        assertEquals("1.00 GB", HostLensFormatter.formatBytes(1024L * 1024L * 1024L));
        assertEquals("1.00 TB", HostLensFormatter.formatBytes(1024L * 1024L * 1024L * 1024L));
    }
}
