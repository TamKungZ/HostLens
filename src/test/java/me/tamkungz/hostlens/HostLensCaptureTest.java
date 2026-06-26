package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLensCaptureTest {

    @Test
    void captureShouldReturnUsableSnapshot() {
        HostSnapshot snapshot = assertDoesNotThrow(HostLens::capture);

        assertNotNull(snapshot);
        assertNotNull(snapshot.capturedAt());
        assertNotNull(snapshot.runtime(), "runtime inspector should always run");
        assertNotNull(snapshot.operatingSystem(), "operating system inspector should always run");
        assertNotNull(snapshot.gpus(), "GPU list must never be null");
        assertNotNull(snapshot.disks(), "disk list must never be null");
        assertNotNull(snapshot.networks(), "network list must never be null");
        assertNotNull(snapshot.properties(), "properties map must never be null");
        assertNotNull(snapshot.errors(), "errors list must never be null");
    }

    @Test
    void quickCaptureShouldSkipExpensiveOptionalInspectors() {
        HostSnapshot snapshot = assertDoesNotThrow(HostLens::quickCapture);

        assertNotNull(snapshot.runtime());
        assertNotNull(snapshot.operatingSystem());
        assertNotNull(snapshot.cpu());
        assertNotNull(snapshot.memory());
        assertTrue(snapshot.gpus().isEmpty(), "quickCapture should skip GPU inspection");
        assertTrue(snapshot.disks().isEmpty(), "quickCapture should skip disk inspection");
        assertTrue(snapshot.networks().isEmpty(), "quickCapture should skip network inspection");
    }

    @Test
    void builderShouldRespectDisabledInspectors() {
        HostSnapshot snapshot = assertDoesNotThrow(() -> HostLens.builder()
                .includeCpu(false)
                .includeMemory(false)
                .includeGpu(false)
                .includeDisk(false)
                .includeNetwork(false)
                .capture());

        assertNotNull(snapshot.runtime(), "runtime inspector is always included");
        assertNotNull(snapshot.operatingSystem(), "operating system inspector is always included");
        assertTrue(snapshot.gpus().isEmpty());
        assertTrue(snapshot.disks().isEmpty());
        assertTrue(snapshot.networks().isEmpty());
    }

    @Test
    void nonFailFastCaptureShouldRecordCustomInspectorErrors() {
        HostSnapshot snapshot = HostLens.builder()
                .includeCpu(false)
                .includeMemory(false)
                .includeGpu(false)
                .includeDisk(false)
                .includeNetwork(false)
                .addInspector(new BrokenInspector())
                .capture();

        assertFalse(snapshot.errors().isEmpty());
        assertTrue(snapshot.errors().stream().anyMatch(error -> "broken-test".equals(error.source())));
    }

    @Test
    void failFastCaptureShouldThrowWhenInspectorFails() {
        assertThrows(HostLensException.class, () -> HostLens.builder()
                .includeCpu(false)
                .includeMemory(false)
                .includeGpu(false)
                .includeDisk(false)
                .includeNetwork(false)
                .failFast(true)
                .addInspector(new BrokenInspector())
                .capture());
    }

    private static final class BrokenInspector implements HostInspector {
        @Override
        public String name() {
            return "broken-test";
        }

        @Override
        public boolean supports(HostCaptureContext context) {
            return true;
        }

        @Override
        public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
            throw new IllegalStateException("intentional test failure");
        }
    }
}
