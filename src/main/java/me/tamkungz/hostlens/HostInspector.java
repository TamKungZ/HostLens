package me.tamkungz.hostlens;

public interface HostInspector {

    String name();

    boolean supports(HostCaptureContext context);

    void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) throws Exception;
}