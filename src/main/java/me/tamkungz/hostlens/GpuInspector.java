package me.tamkungz.hostlens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GpuInspector implements HostInspector {
    private static final String UNKNOWN = "unknown";
    private static final Pattern LSPCI_SLOT = Pattern.compile("^([0-9a-fA-F:.]+)\\s+.*");
    private static final Pattern NUMERIC_PCI_ID = Pattern.compile("\\s*\\[[0-9a-fA-F]{4}(?::[0-9a-fA-F]{4})?]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOWS_VENDOR_ID = Pattern.compile("VEN_([0-9A-Fa-f]{4})");

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
        String command = "Get-CimInstance -ClassName Win32_VideoController -ErrorAction SilentlyContinue "
                + "| Sort-Object PNPDeviceID,Name "
                + "| ForEach-Object { "
                + "$values = @(" 
                + "\"$($_.Name)\","
                + "\"$($_.AdapterCompatibility)\","
                + "\"$($_.DriverVersion)\","
                + "\"$($_.PNPDeviceID)\","
                + "\"$($_.VideoProcessor)\","
                + "\"$($_.AdapterDACType)\""
                + "); "
                + "($values | ForEach-Object { $_ -replace \"`t\", \" \" }) -join \"`t\" "
                + "}";

        for (String line : HostLensSupport.command("powershell.exe", "-NoProfile", "-Command", command)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length >= 1 && !isBlank(parts[0])) {
                String name = clean(parts[0]);
                String vendor = parts.length > 1 ? clean(parts[1]) : UNKNOWN;
                String driver = parts.length > 2 ? clean(parts[2]) : UNKNOWN;
                String deviceId = parts.length > 3 ? clean(parts[3]) : UNKNOWN;
                String videoProcessor = parts.length > 4 ? clean(parts[4]) : UNKNOWN;
                String adapterDacType = parts.length > 5 ? clean(parts[5]) : UNKNOWN;
                addGpu(result, new GpuInfo(
                        name,
                        bestVendor(vendor, name, deviceId),
                        driver,
                        deviceId,
                        classifyGpu(name, vendor, deviceId, videoProcessor, adapterDacType)
                ));
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        // Fallback for older Windows installations where CIM/PowerShell is blocked.
        String name = null;
        String vendor = UNKNOWN;
        String driver = UNKNOWN;
        String deviceId = UNKNOWN;
        String videoProcessor = UNKNOWN;
        String adapterDacType = UNKNOWN;
        for (String line : HostLensSupport.command(
                "wmic",
                "path",
                "win32_VideoController",
                "get",
                "Name,AdapterCompatibility,DriverVersion,PNPDeviceID,VideoProcessor,AdapterDACType",
                "/format:list"
        )) {
            if (line.startsWith("Name=")) {
                if (!isBlank(name)) {
                    addGpu(result, new GpuInfo(
                            name,
                            bestVendor(vendor, name, deviceId),
                            driver,
                            deviceId,
                            classifyGpu(name, vendor, deviceId, videoProcessor, adapterDacType)
                    ));
                    vendor = UNKNOWN;
                    driver = UNKNOWN;
                    deviceId = UNKNOWN;
                    videoProcessor = UNKNOWN;
                    adapterDacType = UNKNOWN;
                }
                name = clean(line.substring("Name=".length()));
            } else if (line.startsWith("AdapterCompatibility=")) {
                vendor = clean(line.substring("AdapterCompatibility=".length()));
            } else if (line.startsWith("DriverVersion=")) {
                driver = clean(line.substring("DriverVersion=".length()));
            } else if (line.startsWith("PNPDeviceID=")) {
                deviceId = clean(line.substring("PNPDeviceID=".length()));
            } else if (line.startsWith("VideoProcessor=")) {
                videoProcessor = clean(line.substring("VideoProcessor=".length()));
            } else if (line.startsWith("AdapterDACType=")) {
                adapterDacType = clean(line.substring("AdapterDACType=".length()));
            }
        }
        if (!isBlank(name)) {
            addGpu(result, new GpuInfo(
                    name,
                    bestVendor(vendor, name, deviceId),
                    driver,
                    deviceId,
                    classifyGpu(name, vendor, deviceId, videoProcessor, adapterDacType)
            ));
        }
        return result;
    }

    private static List<GpuInfo> linuxGpus() {
        List<GpuInfo> result = new ArrayList<>();
        Map<String, String> kernelDrivers = linuxKernelDriversBySlot();
        List<String> lines = HostLensSupport.command(
                "sh",
                "-c",
                "lspci -Dnnmm 2>/dev/null | grep -Ei 'vga|3d|display'"
        );

        for (String line : lines) {
            List<String> fields = splitQuoted(line);
            if (fields.size() < 4) {
                String normalized = clean(line.replace('"', ' '));
                if (!isBlank(normalized)) {
                    addGpu(result, new GpuInfo(normalized, UNKNOWN, UNKNOWN, UNKNOWN, classifyGpu(normalized, UNKNOWN, UNKNOWN)));
                }
                continue;
            }

            String slot = clean(fields.get(0));
            String pciClass = clean(fields.get(1));
            String vendor = stripNumericPciIds(fields.get(2));
            String deviceName = stripNumericPciIds(fields.get(3));
            String deviceId = buildLinuxDeviceId(slot, fields);
            String name = clean(deviceName + " (" + pciClass + ")");
            String driver = kernelDrivers.getOrDefault(slot, UNKNOWN);

            addGpu(result, new GpuInfo(
                    name,
                    bestVendor(vendor, name, deviceId),
                    driver,
                    deviceId,
                    classifyGpu(name, vendor, deviceId, pciClass)
            ));
        }

        if (!result.isEmpty()) {
            return result;
        }

        // Last resort: this normally reports only the active renderer, not every GPU.
        String renderer = null;
        String vendor = UNKNOWN;
        for (String line : HostLensSupport.command("sh", "-c", "glxinfo -B 2>/dev/null")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("opengl renderer string:")) {
                renderer = clean(line.substring(line.indexOf(':') + 1));
            } else if (lower.startsWith("opengl vendor string:")) {
                vendor = clean(line.substring(line.indexOf(':') + 1));
            }
        }
        if (!isBlank(renderer)) {
            addGpu(result, new GpuInfo(renderer, bestVendor(vendor, renderer, UNKNOWN), UNKNOWN, UNKNOWN, classifyGpu(renderer, vendor, UNKNOWN)));
        }
        return result;
    }

    private static Map<String, String> linuxKernelDriversBySlot() {
        Map<String, String> drivers = new HashMap<>();
        String currentSlot = null;
        for (String line : HostLensSupport.command("sh", "-c", "lspci -Dk 2>/dev/null")) {
            Matcher slotMatcher = LSPCI_SLOT.matcher(line);
            if (slotMatcher.matches()) {
                currentSlot = slotMatcher.group(1);
                continue;
            }
            String trimmed = line.trim();
            if (currentSlot != null && trimmed.startsWith("Kernel driver in use:")) {
                drivers.put(currentSlot, clean(trimmed.substring("Kernel driver in use:".length())));
            }
        }
        return drivers;
    }

    private static List<GpuInfo> macGpus() {
        List<GpuInfo> result = new ArrayList<>();
        String name = null;
        String vendor = UNKNOWN;
        String deviceId = UNKNOWN;

        for (String rawLine : HostLensSupport.command("system_profiler", "SPDisplaysDataType")) {
            String line = rawLine.trim();
            if (line.startsWith("Chipset Model:")) {
                if (!isBlank(name)) {
                    addGpu(result, new GpuInfo(name, bestVendor(vendor, name, deviceId), UNKNOWN, deviceId, classifyGpu(name, vendor, deviceId)));
                    vendor = UNKNOWN;
                    deviceId = UNKNOWN;
                }
                name = clean(line.substring("Chipset Model:".length()));
            } else if (line.startsWith("Vendor:")) {
                vendor = clean(line.substring("Vendor:".length()));
            } else if (line.startsWith("Device ID:")) {
                deviceId = clean(line.substring("Device ID:".length()));
            }
        }
        if (!isBlank(name)) {
            addGpu(result, new GpuInfo(name, bestVendor(vendor, name, deviceId), UNKNOWN, deviceId, classifyGpu(name, vendor, deviceId)));
        }
        return result;
    }

    private static void addGpu(List<GpuInfo> result, GpuInfo gpu) {
        if (gpu == null || isBlank(gpu.name())) {
            return;
        }
        String key = canonical(gpu.name()) + "|" + canonical(gpu.deviceId());
        for (GpuInfo existing : result) {
            String existingKey = canonical(existing.name()) + "|" + canonical(existing.deviceId());
            if (existingKey.equals(key)) {
                return;
            }
        }
        result.add(gpu);
    }

    private static String bestVendor(String vendor, String name, String deviceId) {
        String vendorText = clean(vendor);
        if (!UNKNOWN.equalsIgnoreCase(vendorText)) {
            return vendorText;
        }

        String text = canonical(name + " " + deviceId);
        Matcher matcher = WINDOWS_VENDOR_ID.matcher(deviceId == null ? "" : deviceId);
        if (matcher.find()) {
            return switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
                case "10DE" -> "NVIDIA";
                case "8086" -> "Intel";
                case "1002", "1022" -> "AMD";
                case "1414" -> "Microsoft";
                case "15AD" -> "VMware";
                case "80EE" -> "VirtualBox";
                case "1AF4" -> "VirtIO";
                default -> vendorText;
            };
        }
        if (text.contains("nvidia") || text.contains("geforce") || text.contains("quadro") || text.contains("rtx") || text.contains("gtx")) {
            return "NVIDIA";
        }
        if (text.contains("intel")) {
            return "Intel";
        }
        if (text.contains("amd") || text.contains("advanced micro devices") || text.contains("ati") || text.contains("radeon")) {
            return "AMD";
        }
        return vendorText;
    }

    private static String classifyGpu(String... values) {
        String text = canonical(String.join(" ", values));
        if (text.isBlank() || UNKNOWN.equals(text)) {
            return UNKNOWN;
        }

        if (containsAny(text,
                "llvmpipe", "swiftshader", "software rasterizer", "microsoft basic render", "mesa offscreen")) {
            return "software";
        }
        if (containsAny(text,
                "microsoft basic display", "remote display", "vmware", "virtualbox", "virtio", "qxl",
                "parallels", "hyper-v", "bochs", "cirrus logic", "displaylink")) {
            return "virtual";
        }

        Matcher vendorMatcher = WINDOWS_VENDOR_ID.matcher(String.join(" ", values));
        if (vendorMatcher.find()) {
            String vendorId = vendorMatcher.group(1).toUpperCase(Locale.ROOT);
            if ("10DE".equals(vendorId)) {
                return "discrete";
            }
            if ("1414".equals(vendorId) || "15AD".equals(vendorId) || "80EE".equals(vendorId) || "1AF4".equals(vendorId)) {
                return "virtual";
            }
        }

        if (containsAny(text, "nvidia", "geforce", "quadro", "tesla", "rtx", "gtx", "nvs")) {
            return "discrete";
        }

        if (text.contains("intel")) {
            if (containsAny(text, "iris xe max") || text.matches(".*\\barc\\s+(a|b)[0-9].*")) {
                return "discrete";
            }
            return "integrated";
        }

        if (containsAny(text, "apple m1", "apple m2", "apple m3", "apple m4", "apple silicon")) {
            return "integrated";
        }

        if (containsAny(text, "amd", "advanced micro devices", "ati", "radeon")) {
            if (containsAny(text, "firepro", "radeon pro", "radeon rx", " rx ", "rx-", "rx/", "pro w", "radeon vii")) {
                return "discrete";
            }
            if (containsAny(text,
                    "radeon graphics", "radeon(tm) graphics", "ryzen", "apu", "vega 3", "vega 6", "vega 7",
                    "vega 8", "vega 10", "vega 11", "780m", "760m", "680m", "660m", "610m")) {
                return "integrated";
            }
            return UNKNOWN;
        }

        return UNKNOWN;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitQuoted(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (!inQuote && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static String buildLinuxDeviceId(String slot, List<String> fields) {
        StringBuilder builder = new StringBuilder(clean(slot));
        for (String field : fields) {
            Matcher matcher = NUMERIC_PCI_ID.matcher(field);
            while (matcher.find()) {
                String id = matcher.group().replace("[", "").replace("]", "").trim();
                if (!builder.toString().contains(id)) {
                    builder.append(' ').append(id);
                }
            }
        }
        return clean(builder.toString());
    }

    private static String stripNumericPciIds(String value) {
        return clean(NUMERIC_PCI_ID.matcher(value == null ? "" : value).replaceAll(""));
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || UNKNOWN.equalsIgnoreCase(value.trim());
    }
}