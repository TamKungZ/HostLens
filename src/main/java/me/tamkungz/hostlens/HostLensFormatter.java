package me.tamkungz.hostlens;

import java.time.Instant;
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
            builder.append("Name        : ").append(best(os.displayName(), os.name())).append('\n');
            builder.append("Family      : ").append(os.family()).append('\n');
            builder.append("Version     : ").append(os.distributionVersion()).append('\n');
            builder.append("Kernel      : ").append(os.kernelVersion()).append('\n');
            if (!isUnknown(os.buildNumber())) {
                builder.append("Build       : ").append(os.buildNumber()).append('\n');
            }
            if (!isUnknown(os.distribution())) {
                builder.append("Distro      : ").append(os.distribution()).append('\n');
            }
            builder.append("Arch        : ").append(os.architecture()).append('\n');
            if (!isUnknown(os.kernelArchitecture()) && !os.kernelArchitecture().equalsIgnoreCase(os.architecture())) {
                builder.append("Kernel Arch : ").append(os.kernelArchitecture()).append('\n');
            }
            builder.append("Host        : ").append(os.hostName()).append('\n');
            builder.append("User        : ").append(os.userName()).append('\n');
            if (os.wsl()) {
                builder.append("WSL         : true\n");
            }
            if (os.container()) {
                builder.append("Container   : true\n");
            }
        }

        if (snapshot.runtime() != null) {
            RuntimeInfo runtime = snapshot.runtime();
            builder.append("\n[Runtime]\n");
            builder.append("Java        : ").append(runtime.javaVersion());
            if (runtime.javaMajorVersion() >= 0) {
                builder.append(" (major ").append(runtime.javaMajorVersion()).append(')');
            }
            builder.append(" / ").append(runtime.javaVendor()).append('\n');
            if (!isUnknown(runtime.javaVendorVersion())) {
                builder.append("Vendor Ver. : ").append(runtime.javaVendorVersion()).append('\n');
            }
            builder.append("Runtime     : ").append(runtime.javaRuntimeName());
            if (!isUnknown(runtime.javaRuntimeVersion())) {
                builder.append(" ").append(runtime.javaRuntimeVersion());
            }
            builder.append('\n');
            builder.append("Spec        : ").append(runtime.javaSpecificationName())
                    .append(" ").append(runtime.javaSpecificationVersion()).append('\n');
            builder.append("VM          : ").append(runtime.javaVmName())
                    .append(" ").append(runtime.javaVmVersion());
            if (!isUnknown(runtime.javaVmVendor())) {
                builder.append(" / ").append(runtime.javaVmVendor());
            }
            builder.append('\n');
            if (!isUnknown(runtime.javaVmInfo())) {
                builder.append("VM Info     : ").append(runtime.javaVmInfo()).append('\n');
            }
            builder.append("Java Home   : ").append(runtime.javaHome()).append('\n');
            builder.append("PID         : ").append(runtime.processId()).append('\n');
            builder.append("Started At  : ").append(formatEpochMillis(runtime.startTimeMillis())).append('\n');
            builder.append("Uptime      : ").append(runtime.uptimeMillis()).append(" ms\n");
            builder.append("Processors  : ").append(formatCount(runtime.availableProcessors())).append('\n');
            builder.append("Heap        : ").append(formatBytes(runtime.heapUsedBytes()))
                    .append(" used / ").append(formatBytes(runtime.heapMaxBytes())).append(" max")
                    .append(" / ").append(formatBytes(runtime.heapCommittedBytes())).append(" committed\n");
            builder.append("Non-Heap    : ").append(formatBytes(runtime.nonHeapUsedBytes()))
                    .append(" used / ").append(formatBytes(runtime.nonHeapCommittedBytes())).append(" committed\n");
            builder.append("Encoding    : ").append(runtime.fileEncoding());
            if (!isUnknown(runtime.nativeEncoding()) && !runtime.nativeEncoding().equalsIgnoreCase(runtime.fileEncoding())) {
                builder.append(" / native ").append(runtime.nativeEncoding());
            }
            builder.append('\n');
            builder.append("Locale      : ").append(runtime.defaultLocale()).append('\n');
            builder.append("Charset     : ").append(runtime.defaultCharset()).append('\n');
            builder.append("Timezone    : ").append(runtime.timeZone()).append('\n');
            if (!runtime.inputArguments().isEmpty()) {
                builder.append("JVM Args    : ").append(runtime.inputArguments()).append('\n');
            }
        }

        if (snapshot.cpu() != null) {
            CpuInfo cpu = snapshot.cpu();
            builder.append("\n[CPU]\n");
            builder.append("Name        : ").append(cpu.name()).append('\n');
            if (!cpu.vendor().isBlank() && !"unknown".equalsIgnoreCase(cpu.vendor())) {
                builder.append("Vendor      : ").append(cpu.vendor()).append('\n');
            }
            builder.append("Arch        : ").append(cpu.architecture()).append('\n');
            builder.append("Physical    : ").append(formatCount(cpu.physicalCores())).append('\n');
            builder.append("Logical     : ").append(formatCount(cpu.logicalCores())).append('\n');
            builder.append("Available   : ").append(formatCount(cpu.availableProcessors())).append('\n');
            builder.append("Packages    : ").append(formatCount(cpu.packageCount())).append('\n');
            builder.append("Max Clock   : ").append(formatMhz(cpu.maxFrequencyMhz())).append('\n');
            builder.append("Load Avg    : ").append(formatDouble(cpu.systemLoadAverage())).append('\n');
            builder.append("System Load : ").append(formatPercent(cpu.systemCpuLoad())).append('\n');
            builder.append("Process Load: ").append(formatPercent(cpu.processCpuLoad())).append('\n');
        }

        if (snapshot.memory() != null) {
            MemoryInfo memory = snapshot.memory();
            builder.append("\n[Memory]\n");
            builder.append("Physical    : ").append(formatMemoryUsage(memory.physicalUsedBytes(), memory.physicalTotalBytes()))
                    .append(" / available ").append(formatBytes(memory.physicalAvailableBytes()))
                    .append(" / free ").append(formatBytes(memory.physicalFreeBytes())).append('\n');
            builder.append("Swap        : ").append(formatMemoryUsage(memory.swapUsedBytes(), memory.swapTotalBytes()))
                    .append(" / free ").append(formatBytes(memory.swapFreeBytes())).append('\n');
            builder.append("Heap        : ").append(formatJvmMemory(memory.heapUsedBytes(), memory.heapMaxBytes(), memory.heapCommittedBytes())).append('\n');
            builder.append("Non-Heap    : ").append(formatJvmMemory(memory.nonHeapUsedBytes(), memory.nonHeapMaxBytes(), memory.nonHeapCommittedBytes())).append('\n');
            if (memory.cgroupMemoryLimitBytes() >= 0 || memory.cgroupMemoryUsageBytes() >= 0) {
                builder.append("Cgroup Mem  : ").append(formatMemoryUsage(memory.cgroupMemoryUsageBytes(), memory.cgroupMemoryLimitBytes())).append('\n');
            }
            if (memory.cgroupSwapLimitBytes() >= 0 || memory.cgroupSwapUsageBytes() >= 0) {
                builder.append("Cgroup Swap : ").append(formatMemoryUsage(memory.cgroupSwapUsageBytes(), memory.cgroupSwapLimitBytes())).append('\n');
            }
            if (!isUnknown(memory.source())) {
                builder.append("Source      : ").append(memory.source()).append('\n');
            }
        }

        builder.append("\n[GPU]\n");
        if (snapshot.gpus().isEmpty()) {
            builder.append("No GPU data found.\n");
        } else {
            for (GpuInfo gpu : snapshot.gpus()) {
                builder.append("- ").append(gpu.name());
                if (!gpu.type().isBlank() && !"unknown".equalsIgnoreCase(gpu.type())) {
                    builder.append(" [").append(gpu.type()).append(']');
                }
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
                builder.append("- ").append(network.name());
                if (!isUnknown(network.type())) {
                    builder.append(" [").append(network.type()).append(']');
                }
                if (network.primary()) {
                    builder.append(" primary");
                }
                builder.append(" up=").append(network.up())
                        .append(" status=").append(network.status())
                        .append(" mtu=").append(network.mtu());
                if (network.speedMbps() >= 0) {
                    builder.append(" speed=").append(formatSpeedMbps(network.speedMbps()));
                }
                if (!isUnknown(network.macAddress())) {
                    builder.append(" mac=").append(network.macAddress());
                }
                builder.append('\n');

                if (!isUnknown(network.displayName()) && !network.displayName().equals(network.name())) {
                    builder.append("  Display    : ").append(network.displayName()).append('\n');
                }
                if (!network.ipv4Addresses().isEmpty()) {
                    builder.append("  IPv4       : ").append(network.ipv4Addresses()).append('\n');
                }
                if (!network.ipv6Addresses().isEmpty()) {
                    builder.append("  IPv6       : ").append(network.ipv6Addresses()).append('\n');
                }
                if (!network.gateways().isEmpty()) {
                    builder.append("  Gateway    : ").append(network.gateways()).append('\n');
                }
                if (!network.dnsServers().isEmpty()) {
                    builder.append("  DNS        : ").append(network.dnsServers()).append('\n');
                }
                if (!isUnknown(network.dhcp())) {
                    builder.append("  DHCP       : ").append(network.dhcp()).append('\n');
                }
                if (network.loopback() || network.virtual() || network.pointToPoint() || !network.multicast()) {
                    builder.append("  Flags      : loopback=").append(network.loopback())
                            .append(", virtual=").append(network.virtual())
                            .append(", pointToPoint=").append(network.pointToPoint())
                            .append(", multicast=").append(network.multicast())
                            .append('\n');
                }
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

    private static String best(String primary, String fallback) {
        return isUnknown(primary) ? fallback : primary;
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim());
    }

    private static String formatCount(int value) {
        return value < 0 ? "unknown" : Integer.toString(value);
    }

    private static String formatEpochMillis(long value) {
        if (value <= 0) {
            return "unknown";
        }
        return Instant.ofEpochMilli(value).toString();
    }

    private static String formatMhz(double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return "unknown";
        }
        if (value >= 1000.0) {
            return String.format(Locale.ROOT, "%.2f GHz", value / 1000.0);
        }
        return String.format(Locale.ROOT, "%.0f MHz", value);
    }

    private static String formatMemoryUsage(long usedBytes, long totalBytes) {
        if (usedBytes < 0 && totalBytes < 0) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(formatBytes(usedBytes)).append(" used / ").append(formatBytes(totalBytes)).append(" total");
        String percent = formatPercentOf(usedBytes, totalBytes);
        if (!isUnknown(percent)) {
            builder.append(" (").append(percent).append(')');
        }
        return builder.toString();
    }

    private static String formatJvmMemory(long usedBytes, long maxBytes, long committedBytes) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatBytes(usedBytes)).append(" used");
        if (maxBytes >= 0) {
            builder.append(" / ").append(formatBytes(maxBytes)).append(" max");
            String percent = formatPercentOf(usedBytes, maxBytes);
            if (!isUnknown(percent)) {
                builder.append(" (").append(percent).append(')');
            }
        } else {
            builder.append(" / unknown max");
        }
        builder.append(" / ").append(formatBytes(committedBytes)).append(" committed");
        return builder.toString();
    }

    private static String formatPercentOf(long usedBytes, long totalBytes) {
        if (usedBytes < 0 || totalBytes <= 0) {
            return "unknown";
        }
        return formatPercent((double) usedBytes / (double) totalBytes);
    }

    private static String formatSpeedMbps(long value) {
        if (value < 0) {
            return "unknown";
        }
        if (value >= 1000) {
            return String.format(Locale.ROOT, "%.2f Gbps", value / 1000.0);
        }
        return value + " Mbps";
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
