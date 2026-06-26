package me.tamkungz.hostlens;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HostSnapshot(
        Instant capturedAt,
        RuntimeInfo runtime,
        OperatingSystemInfo operatingSystem,
        CpuInfo cpu,
        MemoryInfo memory,
        List<GpuInfo> gpus,
        List<DiskInfo> disks,
        List<NetworkInterfaceInfo> networks,
        Map<String, String> properties,
        List<HostLensError> errors
) {
    public HostSnapshot {
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        gpus = gpus == null ? List.of() : List.copyOf(gpus);
        disks = disks == null ? List.of() : List.copyOf(disks);
        networks = networks == null ? List.of() : List.copyOf(networks);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String toPrettyString() {
        return HostLensFormatter.pretty(this);
    }

    public String toJson() {
        return HostLensJson.toJson(this);
    }

    public static final class Builder {
        private Instant capturedAt = Instant.now();
        private RuntimeInfo runtime;
        private OperatingSystemInfo operatingSystem;
        private CpuInfo cpu;
        private MemoryInfo memory;
        private List<GpuInfo> gpus = List.of();
        private List<DiskInfo> disks = List.of();
        private List<NetworkInterfaceInfo> networks = List.of();
        private Map<String, String> properties = new LinkedHashMap<>();
        private List<HostLensError> errors = List.of();

        private Builder() {
        }

        public Builder capturedAt(Instant capturedAt) {
            this.capturedAt = capturedAt;
            return this;
        }

        public Builder runtime(RuntimeInfo runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder operatingSystem(OperatingSystemInfo operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        public Builder cpu(CpuInfo cpu) {
            this.cpu = cpu;
            return this;
        }

        public Builder memory(MemoryInfo memory) {
            this.memory = memory;
            return this;
        }

        public Builder gpus(List<GpuInfo> gpus) {
            this.gpus = gpus == null ? List.of() : List.copyOf(gpus);
            return this;
        }

        public Builder disks(List<DiskInfo> disks) {
            this.disks = disks == null ? List.of() : List.copyOf(disks);
            return this;
        }

        public Builder networks(List<NetworkInterfaceInfo> networks) {
            this.networks = networks == null ? List.of() : List.copyOf(networks);
            return this;
        }

        public Builder property(String key, String value) {
            if (key != null && value != null) {
                this.properties.put(key, value);
            }
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
            return this;
        }

        public Builder errors(List<HostLensError> errors) {
            this.errors = errors == null ? List.of() : List.copyOf(errors);
            return this;
        }

        public HostSnapshot build() {
            return new HostSnapshot(
                    capturedAt,
                    runtime,
                    operatingSystem,
                    cpu,
                    memory,
                    gpus,
                    disks,
                    networks,
                    properties,
                    errors
            );
        }
    }
}
