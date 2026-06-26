package me.tamkungz.hostlens;

import java.util.List;

public record NetworkInterfaceInfo(
        String name,
        String displayName,
        String macAddress,
        boolean up,
        boolean loopback,
        boolean virtual,
        int mtu,
        List<String> addresses
) {
    public NetworkInterfaceInfo {
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
    }
}
