package me.tamkungz.hostlens;

import java.util.ArrayList;
import java.util.List;

final class GpuInspector implements HostInspector {

    @Override
    public String name() {
        return "gpu";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeGpu();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        List<GpuInfo> gpus;
        if (HostLensSupport.isWindows()) {
            gpus = windowsGpus();
        } else if (HostLensSupport.isLinux()) {
            gpus = linuxGpus();
        } else if (HostLensSupport.isMac()) {
            gpus = macGpus();
        } else {
            gpus = List.of();
        }
        snapshot.gpus(gpus);
    }

    private static List<GpuInfo> windowsGpus() {
        List<GpuInfo> result = new ArrayList<>();
        List<String> lines = HostLensSupport.command(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_VideoController | ForEach-Object { \"$($_.Name)|$($_.AdapterCompatibility)|$($_.DriverVersion)|$($_.PNPDeviceID)\" }"
        );

        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 1 && !parts[0].isBlank()) {
                result.add(new GpuInfo(
                        parts[0].trim(),
                        parts.length > 1 ? parts[1].trim() : "unknown",
                        parts.length > 2 ? parts[2].trim() : "unknown",
                        parts.length > 3 ? parts[3].trim() : "unknown"
                ));
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        String name = null;
        String vendor = "unknown";
        String driver = "unknown";
        String deviceId = "unknown";
        for (String line : HostLensSupport.command("wmic", "path", "win32_VideoController", "get", "Name,AdapterCompatibility,DriverVersion,PNPDeviceID", "/format:list")) {
            if (line.startsWith("Name=")) {
                if (name != null) {
                    result.add(new GpuInfo(name, vendor, driver, deviceId));
                    vendor = "unknown";
                    driver = "unknown";
                    deviceId = "unknown";
                }
                name = line.substring("Name=".length()).trim();
            } else if (line.startsWith("AdapterCompatibility=")) {
                vendor = line.substring("AdapterCompatibility=".length()).trim();
            } else if (line.startsWith("DriverVersion=")) {
                driver = line.substring("DriverVersion=".length()).trim();
            } else if (line.startsWith("PNPDeviceID=")) {
                deviceId = line.substring("PNPDeviceID=".length()).trim();
            }
        }
        if (name != null) {
            result.add(new GpuInfo(name, vendor, driver, deviceId));
        }
        return result;
    }

    private static List<GpuInfo> linuxGpus() {
        List<GpuInfo> result = new ArrayList<>();
        List<String> lines = HostLensSupport.command(
                "sh",
                "-c",
                "lspci -mm 2>/dev/null | grep -Ei 'vga|3d|display'"
        );
        for (String line : lines) {
            String normalized = line.replace('"', ' ').trim().replaceAll("\\s+", " ");
            if (!normalized.isBlank()) {
                result.add(new GpuInfo(normalized, "unknown", "unknown", "unknown"));
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        String renderer = null;
        String vendor = "unknown";
        for (String line : HostLensSupport.command("sh", "-c", "glxinfo -B 2>/dev/null")) {
            if (line.toLowerCase().startsWith("opengl renderer string:")) {
                renderer = line.substring(line.indexOf(':') + 1).trim();
            } else if (line.toLowerCase().startsWith("opengl vendor string:")) {
                vendor = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        if (renderer != null) {
            result.add(new GpuInfo(renderer, vendor, "unknown", "unknown"));
        }
        return result;
    }

    private static List<GpuInfo> macGpus() {
        List<GpuInfo> result = new ArrayList<>();
        String name = null;
        String vendor = "unknown";
        String deviceId = "unknown";

        for (String line : HostLensSupport.command("system_profiler", "SPDisplaysDataType")) {
            if (line.startsWith("Chipset Model:")) {
                if (name != null) {
                    result.add(new GpuInfo(name, vendor, "unknown", deviceId));
                    vendor = "unknown";
                    deviceId = "unknown";
                }
                name = line.substring("Chipset Model:".length()).trim();
            } else if (line.startsWith("Vendor:")) {
                vendor = line.substring("Vendor:".length()).trim();
            } else if (line.startsWith("Device ID:")) {
                deviceId = line.substring("Device ID:".length()).trim();
            }
        }
        if (name != null) {
            result.add(new GpuInfo(name, vendor, "unknown", deviceId));
        }
        return result;
    }
}
