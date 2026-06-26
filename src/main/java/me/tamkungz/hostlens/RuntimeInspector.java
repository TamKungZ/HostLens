package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class RuntimeInspector implements HostInspector {

    @Override
    public String name() {
        return "runtime";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return true;
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();

        String javaVersion = HostLensSupport.property("java.version");
        String specificationVersion = HostLensSupport.property("java.specification.version");
        int javaMajorVersion = RuntimeInfo.parseJavaMajorVersion(specificationVersion);
        if (javaMajorVersion < 0) {
            javaMajorVersion = RuntimeInfo.parseJavaMajorVersion(javaVersion);
        }

        snapshot.runtime(new RuntimeInfo(
                javaVersion,
                javaMajorVersion,
                HostLensSupport.property("java.vendor"),
                HostLensSupport.property("java.vendor.version"),
                HostLensSupport.property("java.vm.name"),
                HostLensSupport.property("java.vm.vendor"),
                HostLensSupport.property("java.vm.version"),
                HostLensSupport.property("java.vm.info"),
                HostLensSupport.property("java.runtime.name"),
                HostLensSupport.property("java.runtime.version"),
                HostLensSupport.property("java.specification.name"),
                specificationVersion,
                HostLensSupport.property("java.home"),
                HostLensSupport.property("user.name"),
                HostLensSupport.property("user.home"),
                HostLensSupport.property("user.dir"),
                HostLensSupport.property("file.encoding"),
                HostLensSupport.property("native.encoding"),
                Locale.getDefault().toLanguageTag(),
                Charset.defaultCharset().name(),
                TimeZone.getDefault().getID(),
                runtimeMxBean.getUptime(),
                runtimeMxBean.getStartTime(),
                ProcessHandle.current().pid(),
                Runtime.getRuntime().availableProcessors(),
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                sanitizeInputArguments(runtimeMxBean.getInputArguments())
        ));
    }

    private static List<String> sanitizeInputArguments(List<String> inputArguments) {
        if (inputArguments == null || inputArguments.isEmpty()) {
            return List.of();
        }
        return inputArguments.stream()
                .map(RuntimeInspector::redactSensitiveArgument)
                .toList();
    }

    private static String redactSensitiveArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return "";
        }

        int separator = argument.indexOf('=');
        if (separator < 0) {
            return argument.trim();
        }

        String key = argument.substring(0, separator);
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (lowerKey.contains("password")
                || lowerKey.contains("passwd")
                || lowerKey.contains("secret")
                || lowerKey.contains("token")
                || lowerKey.contains("credential")
                || lowerKey.contains("apikey")
                || lowerKey.contains("api.key")
                || lowerKey.contains("accesskey")
                || lowerKey.contains("access.key")
                || lowerKey.contains("privatekey")
                || lowerKey.contains("private.key")) {
            return key + "=<redacted>";
        }
        return argument.trim();
    }
}
