package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
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

        snapshot.runtime(new RuntimeInfo(
                HostLensSupport.property("java.version"),
                HostLensSupport.property("java.vendor"),
                HostLensSupport.property("java.vm.name"),
                HostLensSupport.property("java.vm.version"),
                HostLensSupport.property("java.runtime.name"),
                HostLensSupport.property("java.home"),
                HostLensSupport.property("user.name"),
                HostLensSupport.property("user.home"),
                HostLensSupport.property("user.dir"),
                HostLensSupport.property("file.encoding"),
                Locale.getDefault().toLanguageTag(),
                Charset.defaultCharset().name(),
                TimeZone.getDefault().getID(),
                runtimeMxBean.getUptime(),
                ProcessHandle.current().pid(),
                Runtime.getRuntime().availableProcessors()
        ));
    }
}
