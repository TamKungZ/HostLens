package me.tamkungz.hostlens;

public record DiskInfo(
        String name,
        String mount,
        String fileSystem,
        String type,
        long totalBytes,
        long usableBytes,
        long unallocatedBytes,
        boolean readOnly,
        long usedBytes,
        double usedPercent,
        String device,
        String volumeName,
        String driveType,
        String mediaType,
        boolean rootMount,
        boolean systemMount,
        boolean removable,
        boolean network,
        boolean virtual,
        boolean ssd,
        String source
) {
    public DiskInfo {
        name = normalize(name, "unknown");
        mount = normalize(mount, "unknown");
        fileSystem = normalize(fileSystem, "unknown");
        type = normalize(type, "unknown");
        device = normalize(device, "unknown");
        volumeName = normalize(volumeName, "");
        driveType = normalize(driveType, "unknown");
        mediaType = normalize(mediaType, "unknown");
        source = normalize(source, "java-filestore");

        totalBytes = sanitizeBytes(totalBytes);
        usableBytes = sanitizeBytes(usableBytes);
        unallocatedBytes = sanitizeBytes(unallocatedBytes);

        if (usedBytes < 0 && totalBytes >= 0 && usableBytes >= 0) {
            usedBytes = Math.max(0L, totalBytes - usableBytes);
        }
        if ((Double.isNaN(usedPercent) || Double.isInfinite(usedPercent) || usedPercent < 0.0)
                && totalBytes > 0 && usedBytes >= 0) {
            usedPercent = (usedBytes * 100.0) / totalBytes;
        }
    }

    public DiskInfo(
            String name,
            String mount,
            String fileSystem,
            String type,
            long totalBytes,
            long usableBytes,
            long unallocatedBytes,
            boolean readOnly
    ) {
        this(
                name,
                mount,
                fileSystem,
                type,
                totalBytes,
                usableBytes,
                unallocatedBytes,
                readOnly,
                usedBytes(totalBytes, usableBytes),
                usedPercent(totalBytes, usableBytes),
                "unknown",
                "",
                inferDriveType(type, mount),
                inferMediaType(name, type, mount),
                isRootMount(mount),
                isSystemMount(mount),
                false,
                inferDriveType(type, mount).equals("network"),
                inferDriveType(type, mount).equals("virtual"),
                inferMediaType(name, type, mount).equals("ssd") || inferMediaType(name, type, mount).equals("nvme"),
                "java-filestore"
        );
    }

    private static long sanitizeBytes(long value) {
        final long oneExbibyte = 1L << 60;
        if (value < 0 || value >= oneExbibyte) {
            return -1L;
        }
        return value;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static long usedBytes(long totalBytes, long usableBytes) {
        if (totalBytes < 0 || usableBytes < 0) {
            return -1L;
        }
        return Math.max(0L, totalBytes - usableBytes);
    }

    private static double usedPercent(long totalBytes, long usableBytes) {
        long used = usedBytes(totalBytes, usableBytes);
        if (totalBytes <= 0 || used < 0) {
            return -1.0;
        }
        return (used * 100.0) / totalBytes;
    }

    private static boolean isRootMount(String mount) {
        String value = normalize(mount, "");
        return value.equals("/") || value.matches("^[A-Za-z]:\\\\?$");
    }

    private static boolean isSystemMount(String mount) {
        String value = normalize(mount, "").replace('\\', '/').toLowerCase();
        return value.equals("/")
                || value.equals("c:/")
                || value.equals("c:")
                || value.equals("/system")
                || value.equals("/boot")
                || value.startsWith("/usr")
                || value.startsWith("/var")
                || value.startsWith("/private/var");
    }

    private static String inferDriveType(String type, String mount) {
        String value = (normalize(type, "") + " " + normalize(mount, "")).toLowerCase();
        if (containsAny(value, "nfs", "cifs", "smb", "sshfs", "webdav", "afp")) {
            return "network";
        }
        if (containsAny(value, "tmpfs", "ramfs")) {
            return "ramdisk";
        }
        if (containsAny(value, "overlay", "aufs", "squashfs", "fuse", "docker", "container")) {
            return "virtual";
        }
        if (containsAny(value, "cdrom", "iso9660", "udf")) {
            return "optical";
        }
        return "local";
    }

    private static String inferMediaType(String name, String type, String mount) {
        String value = (normalize(name, "") + " " + normalize(type, "") + " " + normalize(mount, "")).toLowerCase();
        if (containsAny(value, "nvme")) {
            return "nvme";
        }
        if (containsAny(value, "ssd", "solid state")) {
            return "ssd";
        }
        if (containsAny(value, "hdd", "hard disk")) {
            return "hdd";
        }
        if (containsAny(value, "usb", "removable")) {
            return "usb";
        }
        if (containsAny(value, "nfs", "cifs", "smb", "afp", "webdav")) {
            return "network";
        }
        if (containsAny(value, "overlay", "tmpfs", "ramfs", "aufs", "squashfs")) {
            return "virtual";
        }
        return "unknown";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
