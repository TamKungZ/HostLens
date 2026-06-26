package me.tamkungz.hostlens;

public record RuntimeInfo(
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
}
