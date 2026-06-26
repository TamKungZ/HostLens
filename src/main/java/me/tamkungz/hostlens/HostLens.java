package me.tamkungz.hostlens;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HostLens {

    private static final List<HostInspector> DEFAULT_INSPECTORS = List.of(
            new RuntimeInspector(),
            new OperatingSystemInspector(),
            new MemoryInspector(),
            new CpuInspector()
            // later:
            // new GpuInspector(),
            // new DiskInspector(),
            // new NetworkInspector()
    );

    private HostLens() {
        throw new UnsupportedOperationException("HostLens is a utility class");
    }

    public static HostSnapshot capture() {
        return builder().capture();
    }

    public static HostSnapshot quickCapture() {
        return builder()
                .includeGpu(false)
                .includeDisk(false)
                .includeNetwork(false)
                .capture();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean includeCpu = true;
        private boolean includeMemory = true;
        private boolean includeGpu = true;
        private boolean includeDisk = true;
        private boolean includeNetwork = true;
        private boolean failFast = false;

        private final List<HostInspector> customInspectors = new ArrayList<>();

        private Builder() {
        }

        public Builder includeCpu(boolean includeCpu) {
            this.includeCpu = includeCpu;
            return this;
        }

        public Builder includeMemory(boolean includeMemory) {
            this.includeMemory = includeMemory;
            return this;
        }

        public Builder includeGpu(boolean includeGpu) {
            this.includeGpu = includeGpu;
            return this;
        }

        public Builder includeDisk(boolean includeDisk) {
            this.includeDisk = includeDisk;
            return this;
        }

        public Builder includeNetwork(boolean includeNetwork) {
            this.includeNetwork = includeNetwork;
            return this;
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public Builder addInspector(HostInspector inspector) {
            customInspectors.add(Objects.requireNonNull(inspector, "inspector"));
            return this;
        }

        public HostSnapshot capture() {
            HostCaptureContext context = new HostCaptureContext(
                    includeCpu,
                    includeMemory,
                    includeGpu,
                    includeDisk,
                    includeNetwork
            );

            HostSnapshot.Builder snapshot = HostSnapshot.builder()
                    .capturedAt(Instant.now());

            List<HostLensError> errors = new ArrayList<>();

            List<HostInspector> inspectors = new ArrayList<>(DEFAULT_INSPECTORS);
            inspectors.addAll(customInspectors);

            for (HostInspector inspector : inspectors) {
                if (!inspector.supports(context)) {
                    continue;
                }

                try {
                    inspector.inspect(context, snapshot);
                } catch (Exception e) {
                    if (failFast) {
                        throw new HostLensException(
                                "Host inspector failed: " + inspector.name(),
                                e
                        );
                    }

                    errors.add(new HostLensError(
                            inspector.name(),
                            e.getClass().getSimpleName(),
                            e.getMessage()
                    ));
                }
            }

            return snapshot.errors(errors).build();
        }
    }
}