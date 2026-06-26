package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class CpuInspector implements HostInspector {

    @Override
    public String name() {
        return "cpu";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeCpu();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        double systemCpuLoad = -1.0;
        double processCpuLoad = -1.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            systemCpuLoad = sunOsBean.getCpuLoad();
            processCpuLoad = sunOsBean.getProcessCpuLoad();
        }

        snapshot.cpu(new CpuInfo(
                detectCpuName(),
                HostLensSupport.property("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                osBean.getAvailableProcessors(),
                osBean.getSystemLoadAverage(),
                systemCpuLoad,
                processCpuLoad
        ));
    }

    private static String detectCpuName() {
        if (HostLensSupport.isWindows()) {
            Optional<String> powershell = HostLensSupport.commandFirstLine(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "(Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name)"
            );
            if (powershell.isPresent()) {
                return powershell.get();
            }

            return HostLensSupport.command("wmic", "cpu", "get", "Name", "/value").stream()
                    .filter(line -> line.startsWith("Name="))
                    .map(line -> line.substring("Name=".length()).trim())
                    .filter(line -> !line.isBlank())
                    .findFirst()
                    .orElse("unknown");
        }

        if (HostLensSupport.isLinux()) {
            List<String> lines = HostLensSupport.readAllLines(Path.of("/proc/cpuinfo"));
            for (String line : lines) {
                String lower = line.toLowerCase();
                if (lower.startsWith("model name") || lower.startsWith("hardware")) {
                    int index = line.indexOf(':');
                    if (index >= 0 && index + 1 < line.length()) {
                        return line.substring(index + 1).trim();
                    }
                }
            }
        }

        if (HostLensSupport.isMac()) {
            return HostLensSupport.commandFirstLine("sysctl", "-n", "machdep.cpu.brand_string")
                    .orElse("unknown");
        }

        return "unknown";
    }
}
