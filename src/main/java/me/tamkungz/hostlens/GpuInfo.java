package me.tamkungz.hostlens;

import java.util.Locale;

public record GpuInfo(
        String name,
        String vendor,
        String driverVersion,
        String deviceId,
        String type
) {
    public GpuInfo {
        name = normalize(name);
        vendor = normalize(vendor);
        driverVersion = normalize(driverVersion);
        deviceId = normalize(deviceId);
        type = normalize(type).toLowerCase(Locale.ROOT);
    }

    public GpuInfo(String name, String vendor, String driverVersion, String deviceId) {
        this(name, vendor, driverVersion, deviceId, "unknown");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}