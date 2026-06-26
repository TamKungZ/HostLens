package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

        CpuDetails details = detectCpuDetails();
        int runtimeAvailable = Runtime.getRuntime().availableProcessors();
        int osAvailable = osBean.getAvailableProcessors();
        int availableProcessors = firstPositive(runtimeAvailable, osAvailable);
        int logicalCores = firstPositive(details.logicalCores(), osAvailable, runtimeAvailable);

        snapshot.cpu(new CpuInfo(
                details.name(),
                details.vendor(),
                HostLensSupport.property("os.arch"),
                details.physicalCores(),
                logicalCores,
                availableProcessors,
                details.packageCount(),
                details.maxFrequencyMhz(),
                osBean.getSystemLoadAverage(),
                systemCpuLoad,
                processCpuLoad,
                details.processorId()
        ));
    }

    private static CpuDetails detectCpuDetails() {
        if (HostLensSupport.isWindows()) {
            CpuDetails windows = windowsCpuDetails();
            if (!windows.isEmpty()) {
                return windows;
            }
        }

        if (HostLensSupport.isLinux()) {
            CpuDetails linux = linuxCpuDetails();
            if (!linux.isEmpty()) {
                return linux;
            }
        }

        if (HostLensSupport.isMac()) {
            CpuDetails mac = macCpuDetails();
            if (!mac.isEmpty()) {
                return mac;
            }
        }

        return CpuDetails.unknown();
    }

    private static CpuDetails windowsCpuDetails() {
        List<String> lines = HostLensSupport.command(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_Processor | ForEach-Object { \"$($_.Name)|$($_.Manufacturer)|$($_.NumberOfCores)|$($_.NumberOfLogicalProcessors)|$($_.MaxClockSpeed)|$($_.ProcessorId)|$($_.SocketDesignation)\" }"
        );

        CpuAccumulator accumulator = new CpuAccumulator();
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2 || isBlank(parts[0])) {
                continue;
            }
            accumulator.addPackage(
                    parts[0],
                    parts[1],
                    parseInt(part(parts, 2), -1),
                    parseInt(part(parts, 3), -1),
                    parseDouble(part(parts, 4), -1.0),
                    part(parts, 5)
            );
        }

        CpuDetails details = accumulator.toDetails();
        if (!details.isEmpty()) {
            return details;
        }

        return windowsCpuDetailsFromWmic();
    }

    private static CpuDetails windowsCpuDetailsFromWmic() {
        List<String> lines = HostLensSupport.command(
                "wmic",
                "cpu",
                "get",
                "Name,Manufacturer,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed,ProcessorId,SocketDesignation",
                "/format:list"
        );

        CpuAccumulator accumulator = new CpuAccumulator();
        String name = null;
        String vendor = "unknown";
        String processorId = "unknown";
        int physical = -1;
        int logical = -1;
        double maxMhz = -1.0;

        for (String line : lines) {
            if (line.startsWith("Name=")) {
                if (!isBlank(name)) {
                    accumulator.addPackage(name, vendor, physical, logical, maxMhz, processorId);
                }
                name = valueAfterEquals(line);
                vendor = "unknown";
                processorId = "unknown";
                physical = -1;
                logical = -1;
                maxMhz = -1.0;
            } else if (line.startsWith("Manufacturer=")) {
                vendor = valueAfterEquals(line);
            } else if (line.startsWith("NumberOfCores=")) {
                physical = parseInt(valueAfterEquals(line), -1);
            } else if (line.startsWith("NumberOfLogicalProcessors=")) {
                logical = parseInt(valueAfterEquals(line), -1);
            } else if (line.startsWith("MaxClockSpeed=")) {
                maxMhz = parseDouble(valueAfterEquals(line), -1.0);
            } else if (line.startsWith("ProcessorId=")) {
                processorId = valueAfterEquals(line);
            }
        }

        if (!isBlank(name)) {
            accumulator.addPackage(name, vendor, physical, logical, maxMhz, processorId);
        }

        return accumulator.toDetails();
    }

    private static CpuDetails linuxCpuDetails() {
        List<String> lines = HostLensSupport.readAllLines(Path.of("/proc/cpuinfo"));
        if (lines.isEmpty()) {
            return linuxFallbackDetails();
        }

        String name = "unknown";
        String vendor = "unknown";
        String processorId = "unknown";
        int logicalCores = 0;
        int coresPerPackage = -1;
        double cpuInfoMhz = -1.0;
        String currentPhysicalId = "0";
        String currentCoreId = null;
        boolean sawProcessor = false;
        Set<String> packageIds = new LinkedHashSet<>();
        Set<String> uniqueCoreIds = new LinkedHashSet<>();

        for (String line : lines) {
            int index = line.indexOf(':');
            if (index < 0) {
                continue;
            }

            String key = line.substring(0, index).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(index + 1).trim();

            switch (key) {
                case "processor" -> {
                    if (sawProcessor) {
                        addLinuxCore(uniqueCoreIds, currentPhysicalId, currentCoreId);
                    }
                    sawProcessor = true;
                    logicalCores++;
                    currentPhysicalId = "0";
                    currentCoreId = null;
                }
                case "model name", "hardware" -> {
                    if (isUnknown(name) && !isBlank(value)) {
                        name = value;
                    }
                }
                case "vendor_id", "cpu implementer" -> {
                    if (isUnknown(vendor) && !isBlank(value)) {
                        vendor = normalizeCpuVendor(value);
                    }
                }
                case "physical id" -> {
                    currentPhysicalId = isBlank(value) ? "0" : value;
                    packageIds.add(currentPhysicalId);
                }
                case "core id" -> currentCoreId = value;
                case "cpu cores" -> {
                    if (coresPerPackage < 0) {
                        coresPerPackage = parseInt(value, -1);
                    }
                }
                case "cpu mhz" -> cpuInfoMhz = Math.max(cpuInfoMhz, parseDouble(value, -1.0));
                case "serial", "processor id" -> {
                    if (isUnknown(processorId) && !isBlank(value)) {
                        processorId = value;
                    }
                }
                default -> {
                    // Ignore unrelated cpuinfo keys.
                }
            }
        }

        if (sawProcessor) {
            addLinuxCore(uniqueCoreIds, currentPhysicalId, currentCoreId);
        }

        int packageCount = !packageIds.isEmpty() ? packageIds.size() : -1;
        int physicalCores = !uniqueCoreIds.isEmpty() ? uniqueCoreIds.size() : -1;
        if (physicalCores < 0 && coresPerPackage > 0) {
            physicalCores = coresPerPackage * Math.max(packageCount, 1);
        }

        double maxFrequencyMhz = linuxMaxFrequencyMhz();
        if (maxFrequencyMhz < 0) {
            maxFrequencyMhz = cpuInfoMhz;
        }

        return new CpuDetails(
                name,
                vendor,
                physicalCores,
                logicalCores > 0 ? logicalCores : -1,
                packageCount,
                maxFrequencyMhz,
                processorId
        );
    }

    private static CpuDetails linuxFallbackDetails() {
        int logicalCores = HostLensSupport.commandFirstLine("sh", "-c", "getconf _NPROCESSORS_ONLN 2>/dev/null || nproc --all 2>/dev/null")
                .map(line -> parseInt(line, -1))
                .orElse(-1);
        return new CpuDetails("unknown", "unknown", -1, logicalCores, -1, -1.0, "unknown");
    }

    private static double linuxMaxFrequencyMhz() {
        Path cpuRoot = Path.of("/sys/devices/system/cpu");
        if (!Files.isDirectory(cpuRoot)) {
            return -1.0;
        }

        double maxKhz = -1.0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cpuRoot, "cpu[0-9]*")) {
            for (Path cpuPath : stream) {
                Optional<String> cpuInfoMax = HostLensSupport.firstExistingReadableLine(cpuPath.resolve("cpufreq/cpuinfo_max_freq"));
                Optional<String> scalingMax = HostLensSupport.firstExistingReadableLine(cpuPath.resolve("cpufreq/scaling_max_freq"));
                Optional<String> value = cpuInfoMax.or(() -> scalingMax);
                if (value.isPresent()) {
                    maxKhz = Math.max(maxKhz, parseDouble(value.get(), -1.0));
                }
            }
        } catch (Exception ignored) {
            return -1.0;
        }

        return maxKhz > 0 ? maxKhz / 1000.0 : -1.0;
    }

    private static CpuDetails macCpuDetails() {
        String name = HostLensSupport.commandFirstLine("sysctl", "-n", "machdep.cpu.brand_string")
                .or(() -> HostLensSupport.commandFirstLine("sysctl", "-n", "hw.model"))
                .orElse("unknown");
        String vendor = HostLensSupport.commandFirstLine("sysctl", "-n", "machdep.cpu.vendor")
                .map(CpuInspector::normalizeCpuVendor)
                .orElseGet(() -> inferVendorFromName(name));
        int physicalCores = sysctlInt("hw.physicalcpu");
        int logicalCores = sysctlInt("hw.logicalcpu");
        int packageCount = sysctlInt("hw.packages");
        double maxFrequencyMhz = sysctlFrequencyMhz("hw.cpufrequency_max");
        if (maxFrequencyMhz < 0) {
            maxFrequencyMhz = sysctlFrequencyMhz("hw.cpufrequency");
        }

        return new CpuDetails(
                name,
                vendor,
                physicalCores,
                logicalCores,
                packageCount,
                maxFrequencyMhz,
                "unknown"
        );
    }

    private static int sysctlInt(String key) {
        return HostLensSupport.commandFirstLine("sysctl", "-n", key)
                .map(line -> parseInt(line, -1))
                .orElse(-1);
    }

    private static double sysctlFrequencyMhz(String key) {
        return HostLensSupport.commandFirstLine("sysctl", "-n", key)
                .map(line -> parseDouble(line, -1.0))
                .filter(value -> value > 0)
                .map(value -> value / 1_000_000.0)
                .orElse(-1.0);
    }

    private static void addLinuxCore(Set<String> uniqueCoreIds, String physicalId, String coreId) {
        if (!isBlank(coreId)) {
            uniqueCoreIds.add((isBlank(physicalId) ? "0" : physicalId) + ":" + coreId);
        }
    }

    private static String normalizeCpuVendor(String value) {
        String vendor = clean(value);
        String lower = vendor.toLowerCase(Locale.ROOT);
        if (lower.contains("genuineintel") || lower.equals("intel") || lower.contains("intel")) {
            return "Intel";
        }
        if (lower.contains("authenticamd") || lower.equals("amd") || lower.contains("advanced micro devices")) {
            return "AMD";
        }
        if (lower.contains("apple")) {
            return "Apple";
        }
        if (lower.contains("arm") || lower.contains("aarch")) {
            return "ARM";
        }
        if (lower.startsWith("0x41")) {
            return "ARM";
        }
        return isBlank(vendor) ? "unknown" : vendor;
    }

    private static String inferVendorFromName(String name) {
        String lower = clean(name).toLowerCase(Locale.ROOT);
        if (lower.contains("intel")) {
            return "Intel";
        }
        if (lower.contains("amd") || lower.contains("ryzen") || lower.contains("epyc")) {
            return "AMD";
        }
        if (lower.contains("apple") || lower.matches(".*\\bm[1-9]\\b.*")) {
            return "Apple";
        }
        return "unknown";
    }

    private static int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return -1;
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static String valueAfterEquals(String line) {
        int index = line.indexOf('=');
        return index >= 0 && index + 1 < line.length() ? line.substring(index + 1).trim() : "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(clean(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(clean(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isUnknown(String value) {
        return isBlank(value) || "unknown".equalsIgnoreCase(value.trim());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record CpuDetails(
            String name,
            String vendor,
            int physicalCores,
            int logicalCores,
            int packageCount,
            double maxFrequencyMhz,
            String processorId
    ) {
        static CpuDetails unknown() {
            return new CpuDetails("unknown", "unknown", -1, -1, -1, -1.0, "unknown");
        }

        boolean isEmpty() {
            return isUnknown(name)
                    && isUnknown(vendor)
                    && physicalCores < 0
                    && logicalCores < 0
                    && packageCount < 0
                    && maxFrequencyMhz < 0
                    && isUnknown(processorId);
        }
    }

    private static final class CpuAccumulator {
        private String name = "unknown";
        private String vendor = "unknown";
        private String processorId = "unknown";
        private int physicalCores = 0;
        private int logicalCores = 0;
        private int packageCount = 0;
        private double maxFrequencyMhz = -1.0;

        void addPackage(String packageName, String packageVendor, int physical, int logical, double maxMhz, String id) {
            if (isBlank(packageName)) {
                return;
            }
            if (isUnknown(name)) {
                name = packageName.trim();
            }
            if (isUnknown(vendor)) {
                vendor = normalizeCpuVendor(packageVendor);
            }
            if (isUnknown(processorId) && !isBlank(id)) {
                processorId = id.trim();
            }
            if (physical > 0) {
                physicalCores += physical;
            }
            if (logical > 0) {
                logicalCores += logical;
            }
            if (maxMhz > 0) {
                maxFrequencyMhz = Math.max(maxFrequencyMhz, maxMhz);
            }
            packageCount++;
        }

        CpuDetails toDetails() {
            return new CpuDetails(
                    name,
                    vendor,
                    physicalCores > 0 ? physicalCores : -1,
                    logicalCores > 0 ? logicalCores : -1,
                    packageCount > 0 ? packageCount : -1,
                    maxFrequencyMhz,
                    processorId
            );
        }
    }
}
