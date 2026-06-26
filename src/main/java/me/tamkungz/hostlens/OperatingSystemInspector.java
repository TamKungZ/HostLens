package me.tamkungz.hostlens;

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
        snapshot.operatingSystem(new OperatingSystemInfo(
                HostLensSupport.property("os.name"),
                HostLensSupport.property("os.version"),
                HostLensSupport.property("os.arch"),
                HostLensSupport.osFamily(),
                HostLensSupport.isWindows(),
                HostLensSupport.isLinux(),
                HostLensSupport.isMac(),
                HostLensSupport.hostName(),
                HostLensSupport.property("user.name")
        ));
    }
}
