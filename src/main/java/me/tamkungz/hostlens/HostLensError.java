package me.tamkungz.hostlens;

public record HostLensError(
        String source,
        String type,
        String message
) {
}