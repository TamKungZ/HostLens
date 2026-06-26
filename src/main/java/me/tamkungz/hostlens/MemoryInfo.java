package me.tamkungz.hostlens;

public record MemoryInfo(
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long nonHeapCommittedBytes,
        long nonHeapMaxBytes,
        long physicalTotalBytes,
        long physicalFreeBytes,
        long physicalAvailableBytes,
        long physicalUsedBytes,
        long swapTotalBytes,
        long swapFreeBytes,
        long swapUsedBytes,
        long cgroupMemoryLimitBytes,
        long cgroupMemoryUsageBytes,
        long cgroupSwapLimitBytes,
        long cgroupSwapUsageBytes,
        String source
) {
    public MemoryInfo {
        source = normalize(source);
        physicalUsedBytes = normalizeUsed(physicalUsedBytes, physicalTotalBytes, physicalAvailableBytes, physicalFreeBytes);
        swapUsedBytes = normalizeUsed(swapUsedBytes, swapTotalBytes, swapFreeBytes, swapFreeBytes);
    }

    public MemoryInfo(
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long nonHeapUsedBytes,
            long nonHeapCommittedBytes,
            long physicalTotalBytes,
            long physicalFreeBytes,
            long swapTotalBytes,
            long swapFreeBytes
    ) {
        this(
                heapUsedBytes,
                heapCommittedBytes,
                heapMaxBytes,
                nonHeapUsedBytes,
                nonHeapCommittedBytes,
                -1L,
                physicalTotalBytes,
                physicalFreeBytes,
                physicalFreeBytes,
                normalizeUsed(-1L, physicalTotalBytes, physicalFreeBytes, physicalFreeBytes),
                swapTotalBytes,
                swapFreeBytes,
                normalizeUsed(-1L, swapTotalBytes, swapFreeBytes, swapFreeBytes),
                -1L,
                -1L,
                -1L,
                -1L,
                "jvm"
        );
    }

    private static long normalizeUsed(long current, long total, long available, long free) {
        if (current >= 0) {
            return current;
        }
        if (total < 0) {
            return -1L;
        }
        if (available >= 0) {
            return Math.max(0L, total - available);
        }
        if (free >= 0) {
            return Math.max(0L, total - free);
        }
        return -1L;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
