package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordCompatibilityTest {

    @Test
    void legacyInfoConstructorsShouldRemainUsable() {
        CpuInfo cpu = new CpuInfo("CPU", "amd64", 12, 12, -1.0, -1.0, -1.0);
        DiskInfo disk = new DiskInfo("disk", "/", "filesystem", "ext4", 100, 60, 60, false);
        GpuInfo gpu = new GpuInfo("GPU", "vendor", "driver", "device");
        MemoryInfo memory = new MemoryInfo(1, 2, 3, 4, 5, 6, 7, 8, 9);
        NetworkInterfaceInfo network = new NetworkInterfaceInfo("eth0", "Ethernet", "00:11:22:33:44:55", true, false, false, 1500, List.of("127.0.0.1"));
        OperatingSystemInfo os = new OperatingSystemInfo("OS", "1", "amd64", "linux", false, true, false, "host", "user");
        RuntimeInfo runtime = new RuntimeInfo(
                "17",
                "vendor",
                "vm",
                "vm-version",
                "runtime",
                "/java",
                "user",
                "/home/user",
                "/work",
                "UTF-8",
                "en-US",
                "UTF-8",
                "Asia/Bangkok",
                123,
                456,
                12
        );

        assertEquals("CPU", cpu.name());
        assertEquals("/", disk.mount());
        assertEquals("GPU", gpu.name());
        assertEquals(6, memory.physicalTotalBytes());
        assertEquals("eth0", network.name());
        assertEquals("linux", os.family());
        assertEquals("17", runtime.javaVersion());
    }

    @Test
    void snapshotShouldDefensivelyNormalizeNullableCollections() {
        HostSnapshot snapshot = new HostSnapshot(
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(snapshot.gpus());
        assertNotNull(snapshot.disks());
        assertNotNull(snapshot.networks());
        assertNotNull(snapshot.properties());
        assertNotNull(snapshot.errors());
        assertTrue(snapshot.gpus().isEmpty());
        assertTrue(snapshot.disks().isEmpty());
        assertTrue(snapshot.networks().isEmpty());
        assertTrue(snapshot.properties().isEmpty());
        assertTrue(snapshot.errors().isEmpty());
    }

    @Test
    void snapshotBuilderShouldPreserveProperties() {
        HostSnapshot snapshot = HostSnapshot.builder()
                .property("project", "hostlens")
                .properties(Map.of("mode", "test"))
                .property("extra", "value")
                .build();

        assertEquals("test", snapshot.properties().get("mode"));
        assertEquals("value", snapshot.properties().get("extra"));
    }
}
