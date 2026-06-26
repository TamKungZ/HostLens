package me.tamkungz.hostlens;

public record OperatingSystemInfo(
        String name,
        String version,
        String architecture,
        String family,
        boolean windows,
        boolean linux,
        boolean mac,
        String hostName,
        String userName
) {
}
