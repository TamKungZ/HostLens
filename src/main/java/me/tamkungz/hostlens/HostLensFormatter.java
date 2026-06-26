package me.tamkungz.hostlens;

import java.util.Locale;

public final class HostLensFormatter {
    private HostLensFormatter() {
    }

    public static String pretty(HostSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("HostLens Snapshot\n");
        builder.append("Captured At : ").append(snapshot.capturedAt()).append('\n');

        if (snapshot.operatingSystem() != null) {
            OperatingSystemInfo os = snapshot.operatingSystem();
            builder.append("\n[Operating System]\n");
            builder.append("Name        : ").append(os.name()).append(' ').append(os.version()).append('\n');
            builder.append("Arch        : ").append(os.architecture()).append('\n');
            builder.append("Family      : ").append(os.family()).append('\n');
            builder.append("Host        : ").append(os.hostName()).append('\n');
            builder.append("User        : ").append(os.userName()).append('\n');
        }

        if (snapshot.runtime() != null) {
            RuntimeInfo runtime = snapshot.runtime();
            builder.append("\n[Runtime]\n");
            builder.append("Java        : ").append(runtime.javaVersion()).append(" / ").append(runtime.javaVendor()).append('\n');
            builder.append("VM          : ").append(runtime.javaVmName()).append(" ").append(runtime.javaVmVersion()).append('\n');
            builder.append("PID         : ").append(runtime.processId()).append('\n');
            builder.append("Uptime      : ").append(runtime.uptimeMillis()).append(" ms\n");
            builder.append("Timezone    : ").append(runtime.timeZone()).append('\n');
        }

        if (snapshot.cpu() != null) {
            CpuInfo cpu = snapshot.cpu();
            builder.append("\n[CPU]\n");
            builder.append("Name        : ").append(cpu.name()).append('\n');
            builder.append("Arch        : ").append(cpu.architecture()).append('\n');
            builder.append("Logical     : ").append(cpu.logicalCores()).append('\n');
            builder.append("Load Avg    : ").append(formatDouble(cpu.systemLoadAverage())).append('\n');
            builder.append("System Load : ").append(formatPercent(cpu.systemCpuLoad())).append('\n');
            builder.append("Process Load: ").append(formatPercent(cpu.processCpuLoad())).append('\n');
        }

        if (snapshot.memory() != null) {
            MemoryInfo memory = snapshot.memory();
            builder.append("\n[Memory]\n");
            builder.append("Physical    : ").append(formatBytes(memory.physicalFreeBytes()))
                    .append(" free / ").append(formatBytes(memory.physicalTotalBytes())).append(" total\n");
            builder.append("Swap        : ").append(formatBytes(memory.swapFreeBytes()))
                    .append(" free / ").append(formatBytes(memory.swapTotalBytes())).append(" total\n");
            builder.append("Heap        : ").append(formatBytes(memory.heapUsedBytes()))
                    .append(" used / ").append(formatBytes(memory.heapMaxBytes())).append(" max\n");
            builder.append("Non-Heap    : ").append(formatBytes(memory.nonHeapUsedBytes()))
                    .append(" used / ").append(formatBytes(memory.nonHeapCommittedBytes())).append(" committed\n");
        }

        builder.append("\n[GPU]\n");
        if (snapshot.gpus().isEmpty()) {
            builder.append("No GPU data found.\n");
        } else {
            for (GpuInfo gpu : snapshot.gpus()) {
                builder.append("- ").append(gpu.name());
                if (!gpu.vendor().isBlank() && !"unknown".equalsIgnoreCase(gpu.vendor())) {
                    builder.append(" / ").append(gpu.vendor());
                }
                if (!gpu.driverVersion().isBlank() && !"unknown".equalsIgnoreCase(gpu.driverVersion())) {
                    builder.append(" / driver ").append(gpu.driverVersion());
                }
                builder.append('\n');
            }
        }

        builder.append("\n[Disks]\n");
        if (snapshot.disks().isEmpty()) {
            builder.append("No disk data found.\n");
        } else {
            for (DiskInfo disk : snapshot.disks()) {
                builder.append("- ").append(disk.mount())
                        .append(" [").append(disk.type()).append("] ")
                        .append(formatBytes(disk.usableBytes())).append(" free / ")
                        .append(formatBytes(disk.totalBytes())).append(" total")
                        .append(disk.readOnly() ? " readonly" : "")
                        .append('\n');
            }
        }

        builder.append("\n[Network]\n");
        if (snapshot.networks().isEmpty()) {
            builder.append("No network data found.\n");
        } else {
            for (NetworkInterfaceInfo network : snapshot.networks()) {
                builder.append("- ").append(network.name())
                        .append(" up=").append(network.up())
                        .append(" loopback=").append(network.loopback())
                        .append(" mac=").append(network.macAddress())
                        .append(" addresses=").append(network.addresses())
                        .append('\n');
            }
        }

        if (snapshot.hasErrors()) {
            builder.append("\n[Errors]\n");
            for (HostLensError error : snapshot.errors()) {
                builder.append("- ").append(error.source())
                        .append(": ").append(error.type())
                        .append(" - ").append(error.message())
                        .append('\n');
            }
        }

        return builder.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unitIndex = -1;
        do {
            value /= 1024.0;
            unitIndex++;
        } while (value >= 1024.0 && unitIndex + 1 < units.length);
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    private static String formatPercent(double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String formatDouble(double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
