package me.tamkungz.hostlens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record NetworkInterfaceInfo(
        String name,
        String displayName,
        String macAddress,
        boolean up,
        boolean loopback,
        boolean virtual,
        int mtu,
        List<String> addresses,
        int index,
        String type,
        boolean pointToPoint,
        boolean multicast,
        boolean primary,
        String status,
        long speedMbps,
        List<String> ipv4Addresses,
        List<String> ipv6Addresses,
        List<String> gateways,
        List<String> dnsServers,
        String dhcp
) {
    public NetworkInterfaceInfo {
        name = clean(name);
        displayName = clean(displayName);
        macAddress = clean(macAddress);
        type = cleanOr(type, "unknown").toLowerCase(Locale.ROOT);
        status = cleanOr(status, up ? "up" : "down").toLowerCase(Locale.ROOT);
        dhcp = cleanOr(dhcp, "unknown").toLowerCase(Locale.ROOT);
        addresses = immutableUnique(addresses);
        ipv4Addresses = immutableUnique(ipv4Addresses);
        ipv6Addresses = immutableUnique(ipv6Addresses);
        gateways = immutableUnique(gateways);
        dnsServers = immutableUnique(dnsServers);
    }

    public NetworkInterfaceInfo(
            String name,
            String displayName,
            String macAddress,
            boolean up,
            boolean loopback,
            boolean virtual,
            int mtu,
            List<String> addresses
    ) {
        this(
                name,
                displayName,
                macAddress,
                up,
                loopback,
                virtual,
                mtu,
                addresses,
                -1,
                inferBasicType(name, displayName, loopback, virtual),
                false,
                false,
                false,
                up ? "up" : "down",
                -1,
                filterAddresses(addresses, false),
                filterAddresses(addresses, true),
                List.of(),
                List.of(),
                "unknown"
        );
    }

    private static String inferBasicType(String name, String displayName, boolean loopback, boolean virtual) {
        if (loopback) {
            return "loopback";
        }
        String text = (clean(name) + " " + clean(displayName)).toLowerCase(Locale.ROOT);
        if (text.contains("wi-fi") || text.contains("wifi") || text.contains("wireless")
                || text.startsWith("wlan") || text.contains(" wlan") || text.startsWith("wl")) {
            return "wifi";
        }
        if (text.contains("vpn") || text.contains("wireguard") || text.contains("tailscale")
                || text.contains("zerotier") || text.startsWith("tun") || text.startsWith("tap")
                || text.startsWith("ppp") || text.startsWith("wg")) {
            return "vpn";
        }
        if (virtual || text.contains("virtual") || text.contains("vmware") || text.contains("virtualbox")
                || text.contains("hyper-v") || text.contains("docker") || text.startsWith("veth")
                || text.startsWith("br-") || text.startsWith("virbr")) {
            return "virtual";
        }
        if (text.contains("ethernet") || text.startsWith("eth") || text.startsWith("enp")
                || text.startsWith("eno") || text.startsWith("ens")) {
            return "ethernet";
        }
        return "unknown";
    }

    private static List<String> filterAddresses(List<String> addresses, boolean ipv6) {
        List<String> result = new ArrayList<>();
        for (String address : immutableUnique(addresses)) {
            boolean isIpv6 = address.contains(":");
            if (isIpv6 == ipv6) {
                result.add(address);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> immutableUnique(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty()) {
                unique.add(clean);
            }
        }
        return List.copyOf(unique);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanOr(String value, String fallback) {
        String clean = clean(value);
        return clean.isEmpty() ? fallback : clean;
    }
}
