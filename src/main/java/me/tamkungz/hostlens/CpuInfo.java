package me.tamkungz.hostlens;

public record CpuInfo(
        String name,
        String architecture,
        int logicalCores,
        int availableProcessors,
        double systemLoadAverage,
        double systemCpuLoad,
        double processCpuLoad
) {
}
