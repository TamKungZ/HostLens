package me.tamkungz.hostlens;

public record CpuInfo(
        String name,
        String vendor,
        String architecture,
        int physicalCores,
        int logicalCores,
        int availableProcessors,
        int packageCount,
        double maxFrequencyMhz,
        double systemLoadAverage,
        double systemCpuLoad,
        double processCpuLoad,
        String processorId
) {
    public CpuInfo {
        name = normalize(name);
        vendor = normalize(vendor);
        architecture = normalize(architecture);
        processorId = normalize(processorId);
        physicalCores = physicalCores < 0 ? -1 : physicalCores;
        logicalCores = logicalCores < 0 ? -1 : logicalCores;
        availableProcessors = availableProcessors < 0 ? -1 : availableProcessors;
        packageCount = packageCount < 0 ? -1 : packageCount;
        maxFrequencyMhz = maxFrequencyMhz < 0 || Double.isNaN(maxFrequencyMhz) || Double.isInfinite(maxFrequencyMhz)
                ? -1.0
                : maxFrequencyMhz;
    }

    public CpuInfo(
            String name,
            String architecture,
            int logicalCores,
            int availableProcessors,
            double systemLoadAverage,
            double systemCpuLoad,
            double processCpuLoad
    ) {
        this(
                name,
                "unknown",
                architecture,
                -1,
                logicalCores,
                availableProcessors,
                -1,
                -1.0,
                systemLoadAverage,
                systemCpuLoad,
                processCpuLoad,
                "unknown"
        );
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim();
    }
}
