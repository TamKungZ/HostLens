package me.tamkungz.hostlens;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class OperatingSystemInspector implements HostInspector {

    @Override
    public String name() {
        return "operating-system";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return true;
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        String name = HostLensSupport.property("os.name");
        String version = HostLensSupport.property("os.version");
        String architecture = HostLensSupport.property("os.arch");
        String family = HostLensSupport.osFamily();

        OsDetails details = detectDetails(name, version, architecture, family);

        snapshot.operatingSystem(new OperatingSystemInfo(
                name,
                version,
                architecture,
                family,
                HostLensSupport.isWindows(),
                HostLensSupport.isLinux(),
                HostLensSupport.isMac(),
                HostLensSupport.hostName(),
                HostLensSupport.property("user.name"),
                details.displayName(),
                details.distribution(),
                details.distributionVersion(),
                details.kernelVersion(),
                details.buildNumber(),
                details.kernelArchitecture(),
                details.wsl(),
                details.container()
        ));
    }

    private static OsDetails detectDetails(String name, String version, String architecture, String family) {
        if (HostLensSupport.isWindows()) {
            return windowsDetails(name, version, architecture, family);
        }
        if (HostLensSupport.isLinux()) {
            return linuxDetails(name, version, architecture, family);
        }
        if (HostLensSupport.isMac()) {
            return macDetails(name, version, architecture, family);
        }
        return new OsDetails(
                name,
                family,
                version,
                version,
                "unknown",
                architecture,
                false,
                isContainerEnvironment()
        );
    }

    private static OsDetails windowsDetails(String name, String version, String architecture, String family) {
        String displayName = name;
        String distributionVersion = version;
        String buildNumber = "unknown";
        String kernelArchitecture = architecture;

        Optional<String> cim = HostLensSupport.commandFirstLine(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_OperatingSystem | Select-Object -First 1 | ForEach-Object { \"$($_.Caption)|$($_.Version)|$($_.BuildNumber)|$($_.OSArchitecture)\" }"
        );
        if (cim.isPresent()) {
            String[] parts = cim.get().split("\\|", -1);
            displayName = part(parts, 0, displayName);
            distributionVersion = part(parts, 1, distributionVersion);
            buildNumber = part(parts, 2, buildNumber);
            kernelArchitecture = part(parts, 3, kernelArchitecture);
        }

        return new OsDetails(
                displayName,
                "Windows",
                distributionVersion,
                distributionVersion,
                buildNumber,
                kernelArchitecture,
                false,
                isContainerEnvironment()
        );
    }

    private static OsDetails linuxDetails(String name, String version, String architecture, String family) {
        Map<String, String> osRelease = readOsRelease(Path.of("/etc/os-release"));
        if (osRelease.isEmpty()) {
            osRelease = readOsRelease(Path.of("/usr/lib/os-release"));
        }

        String distro = firstNonBlank(osRelease.get("NAME"), osRelease.get("ID"), family);
        String distroVersion = firstNonBlank(osRelease.get("VERSION_ID"), osRelease.get("VERSION"), version);
        String prettyName = firstNonBlank(
                osRelease.get("PRETTY_NAME"),
                joinNonBlank(distro, distroVersion),
                name
        );
        String kernelVersion = HostLensSupport.commandFirstLine("uname", "-r").orElse(version);
        String kernelArchitecture = HostLensSupport.commandFirstLine("uname", "-m").orElse(architecture);

        return new OsDetails(
                prettyName,
                distro,
                distroVersion,
                kernelVersion,
                "unknown",
                kernelArchitecture,
                isWsl(),
                isContainerEnvironment()
        );
    }

    private static OsDetails macDetails(String name, String version, String architecture, String family) {
        String productName = HostLensSupport.commandFirstLine("sw_vers", "-productName").orElse("macOS");
        String productVersion = HostLensSupport.commandFirstLine("sw_vers", "-productVersion").orElse(version);
        String buildNumber = HostLensSupport.commandFirstLine("sw_vers", "-buildVersion").orElse("unknown");
        String kernelVersion = HostLensSupport.commandFirstLine("uname", "-r").orElse(version);
        String kernelArchitecture = HostLensSupport.commandFirstLine("uname", "-m").orElse(architecture);

        return new OsDetails(
                joinNonBlank(productName, productVersion),
                productName,
                productVersion,
                kernelVersion,
                buildNumber,
                kernelArchitecture,
                false,
                isContainerEnvironment()
        );
    }

    private static Map<String, String> readOsRelease(Path path) {
        List<String> lines = HostLensSupport.readAllLines(path);
        if (lines.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int index = line.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = line.substring(0, index).trim();
            String value = unquote(line.substring(index + 1).trim());
            if (!key.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\'", "'");
    }

    private static boolean isWsl() {
        if (!HostLensSupport.isLinux()) {
            return false;
        }
        if (!HostLensSupport.isBlank(System.getenv("WSL_DISTRO_NAME"))
                || !HostLensSupport.isBlank(System.getenv("WSL_INTEROP"))) {
            return true;
        }
        for (String line : HostLensSupport.readAllLines(Path.of("/proc/version"))) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("microsoft") || lower.contains("wsl")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContainerEnvironment() {
        if (!HostLensSupport.isBlank(System.getenv("KUBERNETES_SERVICE_HOST"))
                || !HostLensSupport.isBlank(System.getenv("container"))
                || "true".equalsIgnoreCase(System.getenv("DOTNET_RUNNING_IN_CONTAINER"))) {
            return true;
        }

        if (Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/run/.containerenv"))) {
            return true;
        }

        for (String line : HostLensSupport.readAllLines(Path.of("/proc/1/cgroup"))) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("docker")
                    || lower.contains("kubepods")
                    || lower.contains("containerd")
                    || lower.contains("libpod")
                    || lower.contains("podman")
                    || lower.contains("lxc")) {
                return true;
            }
        }
        return false;
    }

    private static String part(String[] parts, int index, String fallback) {
        if (index < 0 || index >= parts.length || parts[index].isBlank()) {
            return fallback;
        }
        return parts[index].trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!HostLensSupport.isBlank(value)) {
                return value.trim();
            }
        }
        return "unknown";
    }

    private static String joinNonBlank(String first, String second) {
        if (HostLensSupport.isBlank(first)) {
            return firstNonBlank(second);
        }
        if (HostLensSupport.isBlank(second)) {
            return first.trim();
        }
        return first.trim() + " " + second.trim();
    }

    private record OsDetails(
            String displayName,
            String distribution,
            String distributionVersion,
            String kernelVersion,
            String buildNumber,
            String kernelArchitecture,
            boolean wsl,
            boolean container
    ) {
    }
}
