package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MemoryInspector implements HostInspector {

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeMemory();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

        MemoryStats stats = readJvmOperatingSystemMemory();

        if (HostLensSupport.isLinux()) {
            stats.merge(readLinuxProcMeminfo(), "linux-proc-meminfo");
            readLinuxCgroupMemory(stats);
        } else if (HostLensSupport.isWindows()) {
            stats.merge(readWindowsCimMemory(), "windows-cim");
        } else if (HostLensSupport.isMac()) {
            stats.merge(readMacMemory(), "macos-sysctl-vm_stat");
        }

        stats.normalizeDerivedValues();

        snapshot.memory(new MemoryInfo(
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                nonHeap.getMax(),
                stats.physicalTotalBytes,
                stats.physicalFreeBytes,
                stats.physicalAvailableBytes,
                stats.physicalUsedBytes,
                stats.swapTotalBytes,
                stats.swapFreeBytes,
                stats.swapUsedBytes,
                stats.cgroupMemoryLimitBytes,
                stats.cgroupMemoryUsageBytes,
                stats.cgroupSwapLimitBytes,
                stats.cgroupSwapUsageBytes,
                stats.source()
        ));
    }

    private static MemoryStats readJvmOperatingSystemMemory() {
        MemoryStats stats = new MemoryStats("jvm-os-bean");
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            stats.physicalTotalBytes = normalizeBytes(sunOsBean.getTotalMemorySize());
            stats.physicalFreeBytes = normalizeBytes(sunOsBean.getFreeMemorySize());
            stats.physicalAvailableBytes = stats.physicalFreeBytes;
            stats.swapTotalBytes = normalizeBytes(sunOsBean.getTotalSwapSpaceSize());
            stats.swapFreeBytes = normalizeBytes(sunOsBean.getFreeSwapSpaceSize());
        }
        stats.normalizeDerivedValues();
        return stats;
    }

    private static MemoryStats readLinuxProcMeminfo() {
        Map<String, Long> meminfo = new HashMap<>();
        for (String line : HostLensSupport.readAllLines(Path.of("/proc/meminfo"))) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String rest = line.substring(colon + 1).trim();
            String[] tokens = rest.split("\\s+");
            if (tokens.length == 0) {
                continue;
            }
            try {
                long value = Long.parseLong(tokens[0]);
                if (tokens.length > 1 && "kb".equalsIgnoreCase(tokens[1])) {
                    value *= 1024L;
                }
                meminfo.put(key, value);
            } catch (NumberFormatException ignored) {
                // Ignore malformed lines from unusual kernels.
            }
        }

        MemoryStats stats = new MemoryStats("linux-proc-meminfo");
        stats.physicalTotalBytes = meminfo.getOrDefault("MemTotal", -1L);
        stats.physicalFreeBytes = meminfo.getOrDefault("MemFree", -1L);
        stats.physicalAvailableBytes = meminfo.getOrDefault("MemAvailable", stats.physicalFreeBytes);
        stats.swapTotalBytes = meminfo.getOrDefault("SwapTotal", -1L);
        stats.swapFreeBytes = meminfo.getOrDefault("SwapFree", -1L);
        stats.normalizeDerivedValues();
        return stats;
    }

    private static void readLinuxCgroupMemory(MemoryStats stats) {
        long v2Limit = readControlFileBytes(Path.of("/sys/fs/cgroup/memory.max"));
        long v2Usage = readControlFileBytes(Path.of("/sys/fs/cgroup/memory.current"));
        long v2SwapLimit = readControlFileBytes(Path.of("/sys/fs/cgroup/memory.swap.max"));
        long v2SwapUsage = readControlFileBytes(Path.of("/sys/fs/cgroup/memory.swap.current"));

        long v1Limit = readControlFileBytes(Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"));
        long v1Usage = readControlFileBytes(Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes"));
        long v1MemswLimit = readControlFileBytes(Path.of("/sys/fs/cgroup/memory/memory.memsw.limit_in_bytes"));
        long v1MemswUsage = readControlFileBytes(Path.of("/sys/fs/cgroup/memory/memory.memsw.usage_in_bytes"));

        stats.cgroupMemoryLimitBytes = firstPositive(v2Limit, v1Limit);
        stats.cgroupMemoryUsageBytes = firstPositive(v2Usage, v1Usage);

        if (v2SwapLimit >= 0 || v2SwapUsage >= 0) {
            stats.cgroupSwapLimitBytes = v2SwapLimit;
            stats.cgroupSwapUsageBytes = v2SwapUsage;
        } else if (v1MemswLimit >= 0 && v1Limit >= 0) {
            stats.cgroupSwapLimitBytes = Math.max(0L, v1MemswLimit - v1Limit);
            if (v1MemswUsage >= 0 && v1Usage >= 0) {
                stats.cgroupSwapUsageBytes = Math.max(0L, v1MemswUsage - v1Usage);
            }
        }

        if (stats.cgroupMemoryLimitBytes >= 0 || stats.cgroupMemoryUsageBytes >= 0
                || stats.cgroupSwapLimitBytes >= 0 || stats.cgroupSwapUsageBytes >= 0) {
            stats.addSource("linux-cgroup");
        }
    }

    private static MemoryStats readWindowsCimMemory() {
        List<String> lines = HostLensSupport.command(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "$os=Get-CimInstance Win32_OperatingSystem; \"$($os.TotalVisibleMemorySize)|$($os.FreePhysicalMemory)|$($os.TotalVirtualMemorySize)|$($os.FreeVirtualMemory)\""
        );
        if (lines.isEmpty()) {
            return new MemoryStats("windows-cim");
        }

        String[] parts = lines.get(0).split("\\|", -1);
        MemoryStats stats = new MemoryStats("windows-cim");
        long totalPhysical = kilobytesToBytes(part(parts, 0));
        long freePhysical = kilobytesToBytes(part(parts, 1));
        long totalVirtual = kilobytesToBytes(part(parts, 2));
        long freeVirtual = kilobytesToBytes(part(parts, 3));

        stats.physicalTotalBytes = totalPhysical;
        stats.physicalFreeBytes = freePhysical;
        stats.physicalAvailableBytes = freePhysical;

        if (totalVirtual >= 0 && totalPhysical >= 0) {
            stats.swapTotalBytes = Math.max(0L, totalVirtual - totalPhysical);
        }
        if (freeVirtual >= 0 && freePhysical >= 0) {
            stats.swapFreeBytes = Math.max(0L, freeVirtual - freePhysical);
        }
        stats.normalizeDerivedValues();
        return stats;
    }

    private static MemoryStats readMacMemory() {
        MemoryStats stats = new MemoryStats("macos-sysctl-vm_stat");
        stats.physicalTotalBytes = HostLensSupport.commandFirstLine("sysctl", "-n", "hw.memsize")
                .map(MemoryInspector::parseLong)
                .orElse(-1L);

        long pageSize = HostLensSupport.commandFirstLine("pagesize")
                .map(MemoryInspector::parseLong)
                .or(() -> HostLensSupport.commandFirstLine("sysctl", "-n", "hw.pagesize").map(MemoryInspector::parseLong))
                .orElse(4096L);

        Map<String, Long> pages = new HashMap<>();
        for (String line : HostLensSupport.command("vm_stat")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).replace(".", "").trim();
            try {
                pages.put(key, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Ignore the vm_stat header and unexpected lines.
            }
        }

        long freePages = pages.getOrDefault("pages free", 0L);
        long inactivePages = pages.getOrDefault("pages inactive", 0L);
        long speculativePages = pages.getOrDefault("pages speculative", 0L);
        long freeBytes = safeMultiply(freePages + speculativePages, pageSize);
        long availableBytes = safeMultiply(freePages + inactivePages + speculativePages, pageSize);
        stats.physicalFreeBytes = freeBytes > 0 ? freeBytes : -1L;
        stats.physicalAvailableBytes = availableBytes > 0 ? availableBytes : stats.physicalFreeBytes;

        for (String line : HostLensSupport.command("sysctl", "vm.swapusage")) {
            parseMacSwapUsage(line, stats);
        }

        stats.normalizeDerivedValues();
        return stats;
    }

    private static void parseMacSwapUsage(String line, MemoryStats stats) {
        Matcher matcher = Pattern.compile("(total|used|free)\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT]?)", Pattern.CASE_INSENSITIVE)
                .matcher(line);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            double amount = Double.parseDouble(matcher.group(2));
            long bytes = unitToBytes(amount, matcher.group(3));
            switch (key) {
                case "total" -> stats.swapTotalBytes = bytes;
                case "used" -> stats.swapUsedBytes = bytes;
                case "free" -> stats.swapFreeBytes = bytes;
                default -> {
                }
            }
        }
    }

    private static long readControlFileBytes(Path path) {
        Optional<String> line = HostLensSupport.firstExistingReadableLine(path);
        if (line.isEmpty()) {
            return -1L;
        }
        String value = line.get().trim();
        if (value.equalsIgnoreCase("max")) {
            return -1L;
        }
        try {
            long parsed = Long.parseLong(value);
            // cgroup v1 often reports a huge sentinel value when there is no real limit.
            if (parsed < 0 || parsed >= Long.MAX_VALUE / 4) {
                return -1L;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static long kilobytesToBytes(String value) {
        long parsed = parseLong(value);
        return parsed < 0 ? -1L : safeMultiply(parsed, 1024L);
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static long parseLong(String value) {
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static long safeMultiply(long value, long multiplier) {
        if (value < 0 || multiplier < 0) {
            return -1L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return -1L;
        }
        return value * multiplier;
    }

    private static long unitToBytes(double value, String unit) {
        String normalized = unit == null ? "" : unit.trim().toUpperCase(Locale.ROOT);
        double multiplier = switch (normalized) {
            case "T" -> 1024D * 1024D * 1024D * 1024D;
            case "G" -> 1024D * 1024D * 1024D;
            case "M" -> 1024D * 1024D;
            case "K" -> 1024D;
            default -> 1D;
        };
        return Math.round(value * multiplier);
    }

    private static long firstPositive(long first, long second) {
        return first >= 0 ? first : second;
    }

    private static long normalizeBytes(long value) {
        return value < 0 ? -1L : value;
    }

    private static final class MemoryStats {
        private final List<String> sources = new ArrayList<>();
        private long physicalTotalBytes = -1L;
        private long physicalFreeBytes = -1L;
        private long physicalAvailableBytes = -1L;
        private long physicalUsedBytes = -1L;
        private long swapTotalBytes = -1L;
        private long swapFreeBytes = -1L;
        private long swapUsedBytes = -1L;
        private long cgroupMemoryLimitBytes = -1L;
        private long cgroupMemoryUsageBytes = -1L;
        private long cgroupSwapLimitBytes = -1L;
        private long cgroupSwapUsageBytes = -1L;

        private MemoryStats(String source) {
            addSource(source);
        }

        private void merge(MemoryStats other, String source) {
            if (other == null) {
                return;
            }
            physicalTotalBytes = choose(other.physicalTotalBytes, physicalTotalBytes);
            physicalFreeBytes = choose(other.physicalFreeBytes, physicalFreeBytes);
            physicalAvailableBytes = choose(other.physicalAvailableBytes, physicalAvailableBytes);
            physicalUsedBytes = choose(other.physicalUsedBytes, physicalUsedBytes);
            swapTotalBytes = choose(other.swapTotalBytes, swapTotalBytes);
            swapFreeBytes = choose(other.swapFreeBytes, swapFreeBytes);
            swapUsedBytes = choose(other.swapUsedBytes, swapUsedBytes);
            cgroupMemoryLimitBytes = choose(other.cgroupMemoryLimitBytes, cgroupMemoryLimitBytes);
            cgroupMemoryUsageBytes = choose(other.cgroupMemoryUsageBytes, cgroupMemoryUsageBytes);
            cgroupSwapLimitBytes = choose(other.cgroupSwapLimitBytes, cgroupSwapLimitBytes);
            cgroupSwapUsageBytes = choose(other.cgroupSwapUsageBytes, cgroupSwapUsageBytes);
            addSource(source);
        }

        private void normalizeDerivedValues() {
            if (physicalAvailableBytes < 0 && physicalFreeBytes >= 0) {
                physicalAvailableBytes = physicalFreeBytes;
            }
            if (physicalUsedBytes < 0 && physicalTotalBytes >= 0) {
                long freeOrAvailable = physicalAvailableBytes >= 0 ? physicalAvailableBytes : physicalFreeBytes;
                if (freeOrAvailable >= 0) {
                    physicalUsedBytes = Math.max(0L, physicalTotalBytes - freeOrAvailable);
                }
            }
            if (swapUsedBytes < 0 && swapTotalBytes >= 0 && swapFreeBytes >= 0) {
                swapUsedBytes = Math.max(0L, swapTotalBytes - swapFreeBytes);
            }
        }

        private void addSource(String source) {
            if (source == null || source.isBlank()) {
                return;
            }
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }

        private String source() {
            return sources.isEmpty() ? "unknown" : String.join("+", sources);
        }

        private static long choose(long preferred, long fallback) {
            return preferred >= 0 ? preferred : fallback;
        }
    }
}
