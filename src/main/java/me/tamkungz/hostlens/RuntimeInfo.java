package me.tamkungz.hostlens;

import java.util.List;

public record RuntimeInfo(
        String javaVersion,
        int javaMajorVersion,
        String javaVendor,
        String javaVendorVersion,
        String javaVmName,
        String javaVmVendor,
        String javaVmVersion,
        String javaVmInfo,
        String javaRuntimeName,
        String javaRuntimeVersion,
        String javaSpecificationName,
        String javaSpecificationVersion,
        String javaHome,
        String userName,
        String userHome,
        String userDir,
        String fileEncoding,
        String nativeEncoding,
        String defaultLocale,
        String defaultCharset,
        String timeZone,
        long uptimeMillis,
        long startTimeMillis,
        long processId,
        int availableProcessors,
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long nonHeapCommittedBytes,
        List<String> inputArguments
) {
    public RuntimeInfo {
        javaVersion = normalize(javaVersion);
        javaVendor = normalize(javaVendor);
        javaVendorVersion = normalize(javaVendorVersion);
        javaVmName = normalize(javaVmName);
        javaVmVendor = normalize(javaVmVendor);
        javaVmVersion = normalize(javaVmVersion);
        javaVmInfo = normalize(javaVmInfo);
        javaRuntimeName = normalize(javaRuntimeName);
        javaRuntimeVersion = normalize(javaRuntimeVersion);
        javaSpecificationName = normalize(javaSpecificationName);
        javaSpecificationVersion = normalize(javaSpecificationVersion);
        javaHome = normalize(javaHome);
        userName = normalize(userName);
        userHome = normalize(userHome);
        userDir = normalize(userDir);
        fileEncoding = normalize(fileEncoding);
        nativeEncoding = normalize(nativeEncoding);
        defaultLocale = normalize(defaultLocale);
        defaultCharset = normalize(defaultCharset);
        timeZone = normalize(timeZone);
        inputArguments = inputArguments == null ? List.of() : List.copyOf(inputArguments);
    }

    public RuntimeInfo(
            String javaVersion,
            String javaVendor,
            String javaVmName,
            String javaVmVersion,
            String javaRuntimeName,
            String javaHome,
            String userName,
            String userHome,
            String userDir,
            String fileEncoding,
            String defaultLocale,
            String defaultCharset,
            String timeZone,
            long uptimeMillis,
            long processId,
            int availableProcessors
    ) {
        this(
                javaVersion,
                parseJavaMajorVersion(javaVersion),
                javaVendor,
                "unknown",
                javaVmName,
                "unknown",
                javaVmVersion,
                "unknown",
                javaRuntimeName,
                "unknown",
                "unknown",
                "unknown",
                javaHome,
                userName,
                userHome,
                userDir,
                fileEncoding,
                "unknown",
                defaultLocale,
                defaultCharset,
                timeZone,
                uptimeMillis,
                -1L,
                processId,
                availableProcessors,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                List.of()
        );
    }

    static int parseJavaMajorVersion(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        String version = value.trim();
        try {
            if (version.startsWith("1.")) {
                int index = version.indexOf('.', 2);
                String major = index > 0 ? version.substring(2, index) : version.substring(2);
                return Integer.parseInt(major.replaceAll("[^0-9].*", ""));
            }
            return Integer.parseInt(version.replaceAll("[^0-9].*", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
