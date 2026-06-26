package me.tamkungz.hostlens;

public record MemoryInfo(
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
}
