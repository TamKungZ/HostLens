package me.tamkungz.hostlens;

public record GpuInfo(
        String name,
        String vendor,
        String driverVersion,
        String deviceId
) {
}
