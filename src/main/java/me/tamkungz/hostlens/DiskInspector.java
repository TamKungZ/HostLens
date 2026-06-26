package me.tamkungz.hostlens;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DiskInspector implements HostInspector {

    @Override
    public String name() {
        return "disk";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeDisk();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) throws IOException {
        List<DiskInfo> disks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, DiskMetadata> metadata = collectMetadata();

        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                FileStore store = Files.getFileStore(root);
                String mount = normalizeMount(root.toString());
                addStore(disks, seen, store, mount, metadataFor(metadata, mount, store.name()));
            } catch (IOException ignored) {
                // Continue scanning other roots.
            }
        }

        for (Map.Entry<String, DiskMetadata> entry : metadata.entrySet()) {
            String mount = entry.getKey();
            if (HostLensSupport.isBlank(mount) || !isLikelyReadableMount(mount)) {
                continue;
            }
            try {
                Path path = Path.of(mount);
                if (Files.exists(path)) {
                    FileStore store = Files.getFileStore(path);
                    addStore(disks, seen, store, mount, entry.getValue());
                }
            } catch (IOException | RuntimeException ignored) {
                addMetadataOnly(disks, seen, mount, entry.getValue());
            }
        }

        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            String mount = parseMountFromFileStore(store.toString());
            if (HostLensSupport.isLinux() && !isInterestingLinuxMount(new MountEntry(mount, store.type(), store.name(), false))) {
                continue;
            }
            DiskMetadata meta = metadataFor(metadata, mount, store.name());
            addStore(disks, seen, store, mount, meta);
        }

        snapshot.disks(disks);
    }

    private static void addStore(
            List<DiskInfo> disks,
            Set<String> seen,
            FileStore store,
            String mount,
            DiskMetadata meta
    ) throws IOException {
        String resolvedMount = normalizeMount(firstNonBlank(mount, meta.mount(), parseMountFromFileStore(store.toString())));
        String fileSystem = firstNonBlank(meta.fileSystem(), store.toString());
        String type = firstNonBlank(meta.type(), store.type());
        String device = firstNonBlank(meta.device(), store.name());
        long total = sanitizeBytes(nonNegativeOr(store.getTotalSpace(), meta.totalBytes()));
        long usable = sanitizeBytes(nonNegativeOr(store.getUsableSpace(), meta.usableBytes()));
        long unallocated = sanitizeBytes(nonNegativeOr(store.getUnallocatedSpace(), meta.usableBytes()));
        long used = total >= 0 && usable >= 0 ? Math.max(0L, total - usable) : meta.usedBytes();
        double usedPercent = total > 0 && used >= 0 ? (used * 100.0) / total : -1.0;

        String key = key(device, resolvedMount, type);
        if (!seen.add(key)) {
            return;
        }

        disks.add(new DiskInfo(
                firstNonBlank(meta.name(), store.name(), resolvedMount),
                resolvedMount,
                fileSystem,
                type,
                total,
                usable,
                unallocated,
                meta.readOnlyKnown() ? meta.readOnly() : store.isReadOnly(),
                used,
                usedPercent,
                device,
                meta.volumeName(),
                firstNonBlank(meta.driveType(), inferDriveType(type, device, resolvedMount)),
                firstNonBlank(meta.mediaType(), inferMediaType(type, device, resolvedMount)),
                isRootMount(resolvedMount),
                isSystemMount(resolvedMount),
                meta.removable(),
                meta.network() || "network".equals(inferDriveType(type, device, resolvedMount)),
                meta.virtualDisk() || "virtual".equals(inferDriveType(type, device, resolvedMount)),
                meta.ssd() || "ssd".equals(inferMediaType(type, device, resolvedMount)) || "nvme".equals(inferMediaType(type, device, resolvedMount)),
                firstNonBlank(meta.source(), "java-filestore")
        ));
    }

    private static void addMetadataOnly(List<DiskInfo> disks, Set<String> seen, String mount, DiskMetadata meta) {
        String resolvedMount = normalizeMount(firstNonBlank(mount, meta.mount()));
        String device = firstNonBlank(meta.device(), resolvedMount);
        String type = firstNonBlank(meta.type(), meta.fileSystem(), "unknown");
        String key = key(device, resolvedMount, type);
        if (!seen.add(key)) {
            return;
        }
        long total = meta.totalBytes();
        long usable = meta.usableBytes();
        long used = meta.usedBytes() >= 0 ? meta.usedBytes() : (total >= 0 && usable >= 0 ? Math.max(0L, total - usable) : -1L);
        double usedPercent = total > 0 && used >= 0 ? (used * 100.0) / total : -1.0;
        disks.add(new DiskInfo(
                firstNonBlank(meta.name(), device),
                resolvedMount,
                firstNonBlank(meta.fileSystem(), "unknown"),
                type,
                total,
                usable,
                usable,
                meta.readOnly(),
                used,
                usedPercent,
                device,
                meta.volumeName(),
                firstNonBlank(meta.driveType(), inferDriveType(type, device, resolvedMount)),
                firstNonBlank(meta.mediaType(), inferMediaType(type, device, resolvedMount)),
                isRootMount(resolvedMount),
                isSystemMount(resolvedMount),
                meta.removable(),
                meta.network(),
                meta.virtualDisk(),
                meta.ssd(),
                firstNonBlank(meta.source(), "os-metadata")
        ));
    }

    private static Map<String, DiskMetadata> collectMetadata() {
        if (HostLensSupport.isWindows()) {
            return windowsDisks();
        }
        if (HostLensSupport.isLinux()) {
            return linuxDisks();
        }
        if (HostLensSupport.isMac()) {
            return macDisks();
        }
        return Map.of();
    }

    private static Map<String, DiskMetadata> windowsDisks() {
        Map<String, DiskMetadata> result = new LinkedHashMap<>();
        List<String> lines = HostLensSupport.command(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_LogicalDisk | ForEach-Object { \"$($_.DeviceID)|$($_.VolumeName)|$($_.FileSystem)|$($_.DriveType)|$($_.Size)|$($_.FreeSpace)|$($_.ProviderName)\" }"
        );
        if (lines.isEmpty()) {
            lines = HostLensSupport.command(
                    "wmic", "logicaldisk", "get",
                    "DeviceID,VolumeName,FileSystem,DriveType,Size,FreeSpace,ProviderName", "/format:csv"
            );
        }

        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).startsWith("node,")) {
                continue;
            }
            String[] parts = line.contains("|") ? line.split("\\|", -1) : parseWmicCsv(line);
            if (parts.length < 1) {
                continue;
            }
            String deviceId = parts[0].trim();
            if (deviceId.isBlank() || deviceId.equalsIgnoreCase("DeviceID")) {
                continue;
            }
            String mount = normalizeWindowsMount(deviceId);
            String volumeName = part(parts, 1);
            String fs = part(parts, 2);
            int driveTypeCode = parseInt(part(parts, 3), -1);
            long total = parseLong(part(parts, 4), -1L);
            long free = parseLong(part(parts, 5), -1L);
            String provider = part(parts, 6);
            String driveType = windowsDriveType(driveTypeCode);
            boolean network = driveTypeCode == 4;
            boolean removable = driveTypeCode == 2 || driveTypeCode == 5;
            boolean virtual = driveTypeCode == 6;
            result.put(mount, new DiskMetadata(
                    volumeName.isBlank() ? deviceId : volumeName,
                    mount,
                    fs,
                    fs,
                    total,
                    free,
                    -1L,
                    false,
                    false,
                    provider.isBlank() ? deviceId : provider,
                    volumeName,
                    driveType,
                    network ? "network" : (removable ? "removable" : "unknown"),
                    removable,
                    network,
                    virtual,
                    false,
                    "windows-cim"
            ));
        }
        return result;
    }

    private static String[] parseWmicCsv(String line) {
        String[] raw = line.split(",", -1);
        if (raw.length <= 1) {
            return new String[]{line};
        }
        // wmic csv starts with Node, so shift to DeviceID-first if present.
        if (raw.length >= 8) {
            return new String[]{raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7]};
        }
        return raw;
    }

    private static Map<String, DiskMetadata> linuxDisks() {
        Map<String, DiskMetadata> result = new LinkedHashMap<>();
        Map<String, long[]> sizes = linuxDfSizes();
        for (MountEntry mount : linuxMounts()) {
            if (!isInterestingLinuxMount(mount)) {
                continue;
            }
            DeviceDetails details = linuxDeviceDetails(mount.source(), mount.fileSystem());
            String driveType = firstNonBlank(details.driveType(), inferDriveType(mount.fileSystem(), mount.source(), mount.mount()));
            String mediaType = firstNonBlank(details.mediaType(), inferMediaType(mount.fileSystem(), mount.source(), mount.mount()));
            long[] size = sizes.getOrDefault(mount.mount(), new long[]{-1L, -1L, -1L});
            result.put(mount.mount(), new DiskMetadata(
                    firstNonBlank(details.model(), mount.source(), mount.mount()),
                    mount.mount(),
                    mount.fileSystem(),
                    mount.fileSystem(),
                    size[0],
                    size[2],
                    size[1],
                    mount.readOnly(),
                    true,
                    mount.source(),
                    "",
                    driveType,
                    mediaType,
                    details.removable(),
                    "network".equals(driveType),
                    "virtual".equals(driveType),
                    details.ssd(),
                    "linux-mountinfo"
            ));
        }
        return result;
    }

    private static Map<String, long[]> linuxDfSizes() {
        Map<String, long[]> result = new HashMap<>();
        for (String line : HostLensSupport.command("df", "-kP")) {
            if (line.startsWith("Filesystem")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) {
                continue;
            }
            long total = parseLong(parts[1], -1L);
            long used = parseLong(parts[2], -1L);
            long available = parseLong(parts[3], -1L);
            if (total >= 0) total = sanitizeBytes(total * 1024L);
            if (used >= 0) used = sanitizeBytes(used * 1024L);
            if (available >= 0) available = sanitizeBytes(available * 1024L);
            result.put(parts[5], new long[]{total, used, available});
        }
        return result;
    }

    private static List<MountEntry> linuxMounts() {
        List<MountEntry> entries = new ArrayList<>();
        for (String line : HostLensSupport.readAllLines(Path.of("/proc/self/mountinfo"))) {
            int separator = line.indexOf(" - ");
            if (separator < 0) {
                continue;
            }
            String left = line.substring(0, separator);
            String right = line.substring(separator + 3);
            String[] leftParts = left.split(" ");
            String[] rightParts = right.split(" ");
            if (leftParts.length < 6 || rightParts.length < 3) {
                continue;
            }
            String mount = unescapeMount(leftParts[4]);
            String options = leftParts[5];
            String fs = rightParts[0];
            String source = unescapeMount(rightParts[1]);
            entries.add(new MountEntry(mount, fs, source, options.contains("ro")));
        }
        return entries;
    }

    private static boolean isInterestingLinuxMount(MountEntry mount) {
        String fs = mount.fileSystem().toLowerCase(Locale.ROOT);
        String path = mount.mount();
        Set<String> pseudo = Set.of(
                "proc", "sysfs", "cgroup", "cgroup2", "devpts", "devtmpfs", "mqueue", "debugfs",
                "tracefs", "securityfs", "pstore", "bpf", "hugetlbfs", "fusectl", "efivarfs",
                "binfmt_misc", "nsfs", "rpc_pipefs", "autofs"
        );
        if (pseudo.contains(fs)) {
            return false;
        }
        if (!path.startsWith("/")) {
            return false;
        }
        if (path.startsWith("/proc") || path.startsWith("/sys") || path.startsWith("/etc/")) {
            return false;
        }
        if ((path.startsWith("/run") || path.startsWith("/dev")) && !path.equals("/dev/shm")) {
            return false;
        }
        return true;
    }

    private static DeviceDetails linuxDeviceDetails(String source, String fileSystem) {
        String lower = (source + " " + fileSystem).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "nfs", "cifs", "smb", "sshfs", "webdav")) {
            return new DeviceDetails("", "network", "network", false, true, false);
        }
        if (containsAny(lower, "tmpfs", "ramfs")) {
            return new DeviceDetails("", "ramdisk", "virtual", false, true, false);
        }
        if (containsAny(lower, "overlay", "aufs", "squashfs")) {
            return new DeviceDetails("", "virtual", "virtual", false, true, false);
        }
        if (!source.startsWith("/dev/")) {
            return new DeviceDetails("", "unknown", "unknown", false, false, false);
        }

        String block = source.substring("/dev/".length());
        block = block.replaceFirst("^mapper/", "dm-");
        String base = baseBlockDevice(block);
        Path sys = Path.of("/sys/class/block", base);
        String model = HostLensSupport.firstExistingReadableLine(sys.resolve("device/model")).orElse("");
        boolean removable = "1".equals(HostLensSupport.firstExistingReadableLine(sys.resolve("removable")).orElse("0"));
        Optional<String> rotational = HostLensSupport.firstExistingReadableLine(sys.resolve("queue/rotational"));
        boolean ssd = rotational.isPresent() && "0".equals(rotational.get());
        String media = "unknown";
        if (base.startsWith("nvme")) {
            media = "nvme";
            ssd = true;
        } else if (rotational.isPresent()) {
            media = "0".equals(rotational.get()) ? "ssd" : "hdd";
        } else if (removable) {
            media = "removable";
        }
        return new DeviceDetails(model, removable ? "removable" : "local", media, removable, false, ssd);
    }

    private static String baseBlockDevice(String block) {
        if (block.startsWith("nvme") || block.startsWith("mmcblk")) {
            return block.replaceFirst("p\\d+$", "");
        }
        if (block.startsWith("dm-")) {
            return block;
        }
        return block.replaceFirst("\\d+$", "");
    }

    private static Map<String, DiskMetadata> macDisks() {
        Map<String, DiskMetadata> result = new LinkedHashMap<>();
        for (String line : HostLensSupport.command("df", "-kP")) {
            if (line.startsWith("Filesystem")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) {
                continue;
            }
            String source = parts[0];
            long total = parseLong(parts[1], -1L) * 1024L;
            long used = parseLong(parts[2], -1L) * 1024L;
            long free = parseLong(parts[3], -1L) * 1024L;
            String mount = parts[5];
            MacDiskInfo info = macDiskInfo(mount);
            result.put(mount, new DiskMetadata(
                    firstNonBlank(info.volumeName(), source),
                    mount,
                    info.fileSystem(),
                    info.fileSystem(),
                    total,
                    free,
                    used,
                    false,
                    false,
                    firstNonBlank(info.device(), source),
                    info.volumeName(),
                    firstNonBlank(info.driveType(), "local"),
                    firstNonBlank(info.mediaType(), "unknown"),
                    info.removable(),
                    false,
                    info.virtualDisk(),
                    info.ssd(),
                    "macos-df-diskutil"
            ));
        }
        return result;
    }

    private static MacDiskInfo macDiskInfo(String mount) {
        String volumeName = "";
        String fileSystem = "";
        String device = "";
        boolean removable = false;
        boolean virtualDisk = false;
        boolean ssd = false;
        for (String line : HostLensSupport.command("diskutil", "info", mount)) {
            int index = line.indexOf(':');
            if (index < 0) {
                continue;
            }
            String key = line.substring(0, index).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(index + 1).trim();
            switch (key) {
                case "volume name" -> volumeName = value;
                case "file system personality" -> fileSystem = value;
                case "device node" -> device = value;
                case "removable media" -> removable = value.equalsIgnoreCase("yes");
                case "virtual" -> virtualDisk = value.equalsIgnoreCase("yes");
                case "solid state" -> ssd = value.equalsIgnoreCase("yes");
                default -> {
                }
            }
        }
        String driveType = removable ? "removable" : (virtualDisk ? "virtual" : "local");
        String mediaType = ssd ? "ssd" : (virtualDisk ? "virtual" : "unknown");
        return new MacDiskInfo(volumeName, fileSystem, device, driveType, mediaType, removable, virtualDisk, ssd);
    }

    private static DiskMetadata metadataFor(Map<String, DiskMetadata> metadata, String mount, String name) {
        String normalizedMount = normalizeMount(mount);
        DiskMetadata byMount = metadata.get(normalizedMount);
        if (byMount != null) {
            return byMount;
        }
        String normalizedName = normalizeMount(name);
        for (DiskMetadata value : metadata.values()) {
            if (normalizeMount(value.device()).equalsIgnoreCase(normalizedName)
                    || normalizeMount(value.name()).equalsIgnoreCase(normalizedName)) {
                return value;
            }
        }
        return DiskMetadata.empty(normalizedMount);
    }

    private static String parseMountFromFileStore(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        int open = value.lastIndexOf('(');
        int close = value.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String inside = value.substring(open + 1, close).trim();
            if (inside.startsWith("/") || inside.matches("^[A-Za-z]:\\\\?$") || inside.startsWith("\\\\")) {
                return normalizeMount(inside);
            }
        }
        String[] parts = value.trim().split("\\s+");
        for (String part : parts) {
            if (part.startsWith("/") || part.matches("^[A-Za-z]:\\\\?$") || part.startsWith("\\\\")) {
                return normalizeMount(part);
            }
        }
        return value;
    }

    private static boolean isLikelyReadableMount(String mount) {
        return mount.startsWith("/") || mount.matches("^[A-Za-z]:\\\\?$") || mount.startsWith("\\\\");
    }

    private static String normalizeWindowsMount(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.matches("^[A-Za-z]:$")) {
            return clean + "\\";
        }
        return clean;
    }

    private static String normalizeMount(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.matches("^[A-Za-z]:$")) {
            return clean + "\\";
        }
        return clean.isBlank() ? "unknown" : clean;
    }

    private static String unescapeMount(String value) {
        return value.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\");
    }

    private static String windowsDriveType(int type) {
        return switch (type) {
            case 2 -> "removable";
            case 3 -> "local";
            case 4 -> "network";
            case 5 -> "optical";
            case 6 -> "ramdisk";
            default -> "unknown";
        };
    }

    private static String inferDriveType(String type, String device, String mount) {
        String value = (type + " " + device + " " + mount).toLowerCase(Locale.ROOT);
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

    private static String inferMediaType(String type, String device, String mount) {
        String value = (type + " " + device + " " + mount).toLowerCase(Locale.ROOT);
        if (value.contains("nvme")) {
            return "nvme";
        }
        if (value.contains("ssd")) {
            return "ssd";
        }
        if (value.contains("hdd")) {
            return "hdd";
        }
        if (containsAny(value, "nfs", "cifs", "smb", "sshfs", "webdav", "afp")) {
            return "network";
        }
        if (containsAny(value, "tmpfs", "ramfs", "overlay", "aufs", "squashfs")) {
            return "virtual";
        }
        return "unknown";
    }

    private static boolean isRootMount(String mount) {
        String value = normalizeMount(mount);
        return value.equals("/") || value.matches("^[A-Za-z]:\\\\?$");
    }

    private static boolean isSystemMount(String mount) {
        String value = normalizeMount(mount).replace('\\', '/').toLowerCase(Locale.ROOT);
        return value.equals("/")
                || value.equals("c:/")
                || value.equals("c:")
                || value.equals("/system")
                || value.equals("/boot")
                || value.startsWith("/usr")
                || value.startsWith("/var")
                || value.startsWith("/private/var");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "unknown";
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length && parts[index] != null ? parts[index].trim() : "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long sanitizeBytes(long value) {
        // Some virtual or pseudo file stores report absurd sentinel values near exabyte scale.
        // Treat those as unknown instead of presenting misleading capacity.
        final long oneExbibyte = 1L << 60;
        if (value < 0 || value >= oneExbibyte) {
            return -1L;
        }
        return value;
    }

    private static long nonNegativeOr(long primary, long fallback) {
        return primary >= 0 ? primary : fallback;
    }

    private static String key(String device, String mount, String type) {
        return firstNonBlank(device, "unknown") + "|" + firstNonBlank(mount, "unknown") + "|" + firstNonBlank(type, "unknown");
    }

    private record DiskMetadata(
            String name,
            String mount,
            String fileSystem,
            String type,
            long totalBytes,
            long usableBytes,
            long usedBytes,
            boolean readOnly,
            boolean readOnlyKnown,
            String device,
            String volumeName,
            String driveType,
            String mediaType,
            boolean removable,
            boolean network,
            boolean virtualDisk,
            boolean ssd,
            String source
    ) {
        static DiskMetadata empty(String mount) {
            return new DiskMetadata("", mount, "", "", -1L, -1L, -1L, false, false,
                    "", "", "", "", false, false, false, false, "");
        }
    }

    private record MountEntry(String mount, String fileSystem, String source, boolean readOnly) {
    }

    private record DeviceDetails(String model, String driveType, String mediaType, boolean removable, boolean virtualDisk, boolean ssd) {
    }

    private record MacDiskInfo(
            String volumeName,
            String fileSystem,
            String device,
            String driveType,
            String mediaType,
            boolean removable,
            boolean virtualDisk,
            boolean ssd
    ) {
    }
}
