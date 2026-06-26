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
        String userName,
        String displayName,
        String distribution,
        String distributionVersion,
        String kernelVersion,
        String buildNumber,
        String kernelArchitecture,
        boolean wsl,
        boolean container
) {
    public OperatingSystemInfo {
        name = normalize(name);
        version = normalize(version);
        architecture = normalize(architecture);
        family = normalize(family);
        hostName = normalize(hostName);
        userName = normalize(userName);
        displayName = normalize(displayName);
        distribution = normalize(distribution);
        distributionVersion = normalize(distributionVersion);
        kernelVersion = normalize(kernelVersion);
        buildNumber = normalize(buildNumber);
        kernelArchitecture = normalize(kernelArchitecture);
    }

    public OperatingSystemInfo(
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
        this(
                name,
                version,
                architecture,
                family,
                windows,
                linux,
                mac,
                hostName,
                userName,
                name,
                family,
                version,
                version,
                "unknown",
                architecture,
                false,
                false
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
