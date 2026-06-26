package me.tamkungz.hostlens;

public record HostCaptureContext(
        boolean includeCpu,
        boolean includeMemory,
        boolean includeGpu,
        boolean includeDisk,
        boolean includeNetwork
) {
}
