package me.tamkungz.hostlens;

public record DiskInfo(
        String name,
        String mount,
        String fileSystem,
        String type,
        long totalBytes,
        long usableBytes,
        long unallocatedBytes,
        boolean readOnly
) {
}
