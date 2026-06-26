package me.tamkungz.hostlens;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class NetworkInspector implements HostInspector {

    @Override
    public String name() {
        return "network";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeNetwork();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) throws SocketException {
        NetworkInterface.getNetworkInterfaces();
        OsNetworkDetails details = OsNetworkDetails.detect();
        List<NetworkInterfaceInfo> networks = new ArrayList<>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            snapshot.networks(List.of());
            return;
        }

        for (NetworkInterface networkInterface : Collections.list(interfaces)) {
            try {
                networks.add(toInfo(networkInterface, details));
            } catch (SocketException ignored) {
                // Some virtual or transient interfaces can disappear while being inspected.
                // Skipping one broken interface is better than failing the whole snapshot.
            }
        }

        networks.sort(NetworkInspector::compareNetworkPriority);
        snapshot.networks(networks);
    }

    private static NetworkInterfaceInfo toInfo(NetworkInterface networkInterface, OsNetworkDetails details) throws SocketException {
        String name = clean(networkInterface.getName());
        String displayName = clean(networkInterface.getDisplayName());
        String macAddress = macAddress(networkInterface.getHardwareAddress());
        boolean up = networkInterface.isUp();
        boolean loopback = networkInterface.isLoopback();
        boolean virtual = networkInterface.isVirtual();
        boolean pointToPoint = networkInterface.isPointToPoint();
        boolean multicast = networkInterface.supportsMulticast();
        int mtu = networkInterface.getMTU();
        int index = networkInterface.getIndex();

        List<String> addresses = addresses(networkInterface);
        List<String> ipv4Addresses = ipAddresses(networkInterface, false);
        List<String> ipv6Addresses = ipAddresses(networkInterface, true);
        List<String> gateways = details.gatewaysFor(name, displayName, macAddress);
        boolean primary = !gateways.isEmpty() || (!details.defaultInterfaceName.isBlank() && details.defaultInterfaceName.equals(name));
        List<String> dnsServers = details.dnsFor(name, displayName, macAddress, primary);
        String dhcp = details.dhcpFor(name, displayName, macAddress);
        String status = details.statusFor(name, up);
        long speedMbps = details.speedFor(name, displayName, macAddress);
        String type = details.typeFor(name, displayName, macAddress);
        if (isUnknown(type)) {
            type = inferType(name, displayName, loopback, virtual, pointToPoint);
        }

        return new NetworkInterfaceInfo(
                name,
                displayName,
                macAddress,
                up,
                loopback,
                virtual,
                mtu,
                addresses,
                index,
                type,
                pointToPoint,
                multicast,
                primary,
                status,
                speedMbps,
                ipv4Addresses,
                ipv6Addresses,
                gateways,
                dnsServers,
                dhcp
        );
    }

    private static int compareNetworkPriority(NetworkInterfaceInfo left, NetworkInterfaceInfo right) {
        int primary = Boolean.compare(right.primary(), left.primary());
        if (primary != 0) {
            return primary;
        }
        int up = Boolean.compare(right.up(), left.up());
        if (up != 0) {
            return up;
        }
        int loopback = Boolean.compare(left.loopback(), right.loopback());
        if (loopback != 0) {
            return loopback;
        }
        int virtual = Boolean.compare(left.virtual(), right.virtual());
        if (virtual != 0) {
            return virtual;
        }
        return left.name().compareToIgnoreCase(right.name());
    }

    private static List<String> addresses(NetworkInterface networkInterface) {
        List<String> result = new ArrayList<>();
        for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
            String hostAddress = stripZone(address.getHostAddress());
            if (!hostAddress.isBlank()) {
                result.add(hostAddress);
            }
        }
        return immutableUnique(result);
    }

    private static List<String> ipAddresses(NetworkInterface networkInterface, boolean ipv6) {
        List<String> result = new ArrayList<>();
        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
            InetAddress address = interfaceAddress.getAddress();
            if (address == null) {
                continue;
            }
            boolean matches = ipv6 ? address instanceof Inet6Address : address instanceof Inet4Address;
            if (!matches) {
                continue;
            }
            String value = stripZone(address.getHostAddress());
            short prefix = interfaceAddress.getNetworkPrefixLength();
            if (prefix >= 0) {
                value += "/" + prefix;
            }
            result.add(value);
        }
        return immutableUnique(result);
    }

    private static String stripZone(String hostAddress) {
        if (hostAddress == null) {
            return "";
        }
        int zoneIndex = hostAddress.indexOf('%');
        return zoneIndex >= 0 ? hostAddress.substring(0, zoneIndex) : hostAddress;
    }

    private static String macAddress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    private static String inferType(String name, String displayName, boolean loopback, boolean virtual, boolean pointToPoint) {
        if (loopback) {
            return "loopback";
        }
        String text = (clean(name) + " " + clean(displayName)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "wi-fi", "wifi", "wireless", "wlan", "802.11", "airport")
                || text.startsWith("wl")) {
            return "wifi";
        }
        if (containsAny(text, "vpn", "wireguard", "tailscale", "zerotier", "ipsec")
                || startsWithAny(text, "tun", "tap", "ppp", "wg", "utun")) {
            return "vpn";
        }
        if (pointToPoint || startsWithAny(text, "gre", "ipip", "sit")) {
            return "tunnel";
        }
        if (containsAny(text, "bridge") || startsWithAny(text, "br-", "virbr")) {
            return "bridge";
        }
        if (virtual || containsAny(text, "virtual", "vmware", "virtualbox", "hyper-v", "docker", "podman", "wsl")
                || startsWithAny(text, "veth", "vmnet", "vboxnet")) {
            return "virtual";
        }
        if (containsAny(text, "ethernet", "gbe", "2.5g", "10g")
                || startsWithAny(text, "eth", "enp", "eno", "ens", "em")) {
            return "ethernet";
        }
        return "unknown";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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

    private static final class OsNetworkDetails {
        private final Map<String, List<String>> gatewaysByKey = new LinkedHashMap<>();
        private final Map<String, List<String>> dnsByKey = new LinkedHashMap<>();
        private final Map<String, Long> speedByKey = new LinkedHashMap<>();
        private final Map<String, String> typeByKey = new LinkedHashMap<>();
        private final Map<String, String> dhcpByKey = new LinkedHashMap<>();
        private final List<String> globalDns = new ArrayList<>();
        private String defaultInterfaceName = "";

        private static OsNetworkDetails detect() {
            OsNetworkDetails details = new OsNetworkDetails();
            if (HostLensSupport.isWindows()) {
                details.detectWindows();
            } else if (HostLensSupport.isLinux()) {
                details.detectLinux();
            } else if (HostLensSupport.isMac()) {
                details.detectMac();
            }
            return details;
        }

        private List<String> gatewaysFor(String name, String displayName, String macAddress) {
            return lookupList(gatewaysByKey, name, displayName, macAddress);
        }

        private List<String> dnsFor(String name, String displayName, String macAddress, boolean primary) {
            List<String> perInterface = lookupList(dnsByKey, name, displayName, macAddress);
            if (!perInterface.isEmpty()) {
                return perInterface;
            }
            return primary ? immutableUnique(globalDns) : List.of();
        }

        private String dhcpFor(String name, String displayName, String macAddress) {
            String value = lookupString(dhcpByKey, name, displayName, macAddress);
            return isUnknown(value) ? "unknown" : value;
        }

        private String typeFor(String name, String displayName, String macAddress) {
            String value = lookupString(typeByKey, name, displayName, macAddress);
            return isUnknown(value) ? "unknown" : value;
        }

        private String statusFor(String name, boolean javaUp) {
            if (HostLensSupport.isLinux()) {
                String operState = HostLensSupport.firstExistingReadableLine(Path.of("/sys/class/net", name, "operstate"))
                        .orElse("");
                if (!operState.isBlank()) {
                    return operState;
                }
            }
            return javaUp ? "up" : "down";
        }

        private long speedFor(String name, String displayName, String macAddress) {
            long value = lookupLong(speedByKey, name, displayName, macAddress);
            if (value >= 0) {
                return value;
            }
            if (HostLensSupport.isLinux()) {
                String speed = HostLensSupport.firstExistingReadableLine(Path.of("/sys/class/net", name, "speed"))
                        .orElse("");
                return parseLong(speed, -1);
            }
            return -1;
        }

        private void detectWindows() {
            List<String> configurations = HostLensSupport.command(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_NetworkAdapterConfiguration -Filter \"IPEnabled=True\" | ForEach-Object { "
                            + "$gw = if ($_.DefaultIPGateway) { [string]::Join(',', $_.DefaultIPGateway) } else { '' }; "
                            + "$dns = if ($_.DNSServerSearchOrder) { [string]::Join(',', $_.DNSServerSearchOrder) } else { '' }; "
                            + "\"$($_.Description)|$($_.MACAddress)|$($_.DHCPEnabled)|$gw|$dns\" }"
            );
            for (String line : configurations) {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 5) {
                    continue;
                }
                String description = clean(parts[0]);
                String mac = clean(parts[1]);
                String dhcp = parseWindowsBoolean(parts[2]);
                List<String> gateways = splitComma(parts[3]);
                List<String> dns = splitComma(parts[4]);
                addList(gatewaysByKey, description, gateways);
                addList(gatewaysByKey, macKey(mac), gateways);
                addList(dnsByKey, description, dns);
                addList(dnsByKey, macKey(mac), dns);
                putString(dhcpByKey, description, dhcp);
                putString(dhcpByKey, macKey(mac), dhcp);
                if (defaultInterfaceName.isBlank() && !gateways.isEmpty()) {
                    defaultInterfaceName = description;
                }
            }

            List<String> adapters = HostLensSupport.command(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_NetworkAdapter | Where-Object { $_.MACAddress -ne $null } | ForEach-Object { "
                            + "\"$($_.Name)|$($_.MACAddress)|$($_.Speed)|$($_.AdapterType)|$($_.NetConnectionID)\" }"
            );
            for (String line : adapters) {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 5) {
                    continue;
                }
                String name = clean(parts[0]);
                String mac = clean(parts[1]);
                long speed = parseBitsPerSecondAsMbps(parts[2]);
                String type = windowsType(parts[3], name + " " + parts[4]);
                putLong(speedByKey, name, speed);
                putLong(speedByKey, macKey(mac), speed);
                putString(typeByKey, name, type);
                putString(typeByKey, macKey(mac), type);
                if (!clean(parts[4]).isBlank()) {
                    putLong(speedByKey, parts[4], speed);
                    putString(typeByKey, parts[4], type);
                }
            }
        }

        private void detectLinux() {
            for (String line : HostLensSupport.readAllLines(Path.of("/proc/net/route"))) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3 || "Iface".equalsIgnoreCase(parts[0])) {
                    continue;
                }
                String iface = parts[0];
                String destination = parts[1];
                String gateway = linuxRouteHexToIpv4(parts[2]);
                if ("00000000".equals(destination) && !gateway.isBlank() && !"0.0.0.0".equals(gateway)) {
                    addList(gatewaysByKey, iface, List.of(gateway));
                    if (defaultInterfaceName.isBlank()) {
                        defaultInterfaceName = iface;
                    }
                }
            }

            for (String line : HostLensSupport.readAllLines(Path.of("/etc/resolv.conf"))) {
                String trimmed = line.trim();
                if (trimmed.startsWith("nameserver ")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 2) {
                        globalDns.add(parts[1]);
                    }
                }
            }

            for (String line : HostLensSupport.command("sh", "-c", "resolvectl dns 2>/dev/null || systemd-resolve --status 2>/dev/null")) {
                String trimmed = line.trim();
                int open = trimmed.indexOf('(');
                int close = trimmed.indexOf(')');
                int colon = trimmed.indexOf(':');
                if (trimmed.startsWith("Link ") && open >= 0 && close > open && colon > close) {
                    String iface = trimmed.substring(open + 1, close).trim();
                    addList(dnsByKey, iface, splitWhitespace(trimmed.substring(colon + 1)));
                }
            }

            try (var paths = Files.list(Path.of("/sys/class/net"))) {
                paths.forEach(path -> {
                    String iface = path.getFileName().toString();
                    String type = linuxType(iface);
                    putString(typeByKey, iface, type);
                });
            } catch (IOException ignored) {
            }
        }

        private void detectMac() {
            List<String> route = HostLensSupport.command("route", "-n", "get", "default");
            String gateway = "";
            String iface = "";
            for (String line : route) {
                String trimmed = line.trim();
                if (trimmed.startsWith("gateway:")) {
                    gateway = trimmed.substring("gateway:".length()).trim();
                } else if (trimmed.startsWith("interface:")) {
                    iface = trimmed.substring("interface:".length()).trim();
                }
            }
            if (!iface.isBlank()) {
                defaultInterfaceName = iface;
                addList(gatewaysByKey, iface, gateway.isBlank() ? List.of() : List.of(gateway));
            }

            for (String line : HostLensSupport.command("scutil", "--dns")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("nameserver[") && trimmed.contains(":")) {
                    globalDns.add(trimmed.substring(trimmed.indexOf(':') + 1).trim());
                }
            }

            String hardwarePort = "";
            for (String line : HostLensSupport.command("networksetup", "-listallhardwareports")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Hardware Port:")) {
                    hardwarePort = trimmed.substring("Hardware Port:".length()).trim();
                } else if (trimmed.startsWith("Device:")) {
                    String device = trimmed.substring("Device:".length()).trim();
                    putString(typeByKey, device, macHardwarePortType(hardwarePort));
                }
            }
        }

        private List<String> lookupList(Map<String, List<String>> map, String name, String displayName, String macAddress) {
            List<String> values = new ArrayList<>();
            values.addAll(map.getOrDefault(key(name), List.of()));
            values.addAll(map.getOrDefault(key(displayName), List.of()));
            values.addAll(map.getOrDefault(macKey(macAddress), List.of()));
            return immutableUnique(values);
        }

        private String lookupString(Map<String, String> map, String name, String displayName, String macAddress) {
            String value = map.get(key(name));
            if (!isUnknown(value)) {
                return value;
            }
            value = map.get(key(displayName));
            if (!isUnknown(value)) {
                return value;
            }
            value = map.get(macKey(macAddress));
            return isUnknown(value) ? "unknown" : value;
        }

        private long lookupLong(Map<String, Long> map, String name, String displayName, String macAddress) {
            Long value = map.get(key(name));
            if (value != null && value >= 0) {
                return value;
            }
            value = map.get(key(displayName));
            if (value != null && value >= 0) {
                return value;
            }
            value = map.get(macKey(macAddress));
            return value == null ? -1 : value;
        }

        private static void addList(Map<String, List<String>> map, String rawKey, List<String> values) {
            String key = key(rawKey);
            List<String> cleanValues = immutableUnique(values);
            if (key.isBlank() || cleanValues.isEmpty()) {
                return;
            }
            List<String> merged = new ArrayList<>(map.getOrDefault(key, List.of()));
            merged.addAll(cleanValues);
            map.put(key, immutableUnique(merged));
        }

        private static void putString(Map<String, String> map, String rawKey, String value) {
            String key = key(rawKey);
            String clean = clean(value);
            if (!key.isBlank() && !clean.isBlank()) {
                map.put(key, clean);
            }
        }

        private static void putLong(Map<String, Long> map, String rawKey, long value) {
            String key = key(rawKey);
            if (!key.isBlank() && value >= 0) {
                map.put(key, value);
            }
        }
    }

    private static String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String macKey(String value) {
        String clean = clean(value);
        if (clean.isBlank()) {
            return "";
        }
        return clean.replace("-", ":").toUpperCase(Locale.ROOT);
    }

    private static List<String> splitComma(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String clean = clean(part);
            if (!clean.isBlank()) {
                result.add(clean);
            }
        }
        return immutableUnique(result);
    }

    private static List<String> splitWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.trim().split("\\s+")) {
            String clean = clean(part);
            if (!clean.isBlank()) {
                result.add(clean);
            }
        }
        return immutableUnique(result);
    }

    private static String parseWindowsBoolean(String value) {
        String clean = clean(value).toLowerCase(Locale.ROOT);
        if ("true".equals(clean)) {
            return "enabled";
        }
        if ("false".equals(clean)) {
            return "disabled";
        }
        return "unknown";
    }

    private static long parseBitsPerSecondAsMbps(String value) {
        long bitsPerSecond = parseLong(value, -1);
        if (bitsPerSecond < 0) {
            return -1;
        }
        return Math.max(0, Math.round(bitsPerSecond / 1_000_000.0));
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(clean(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String linuxRouteHexToIpv4(String value) {
        String hex = clean(value);
        if (hex.length() != 8) {
            return "";
        }
        try {
            int b1 = Integer.parseInt(hex.substring(6, 8), 16);
            int b2 = Integer.parseInt(hex.substring(4, 6), 16);
            int b3 = Integer.parseInt(hex.substring(2, 4), 16);
            int b4 = Integer.parseInt(hex.substring(0, 2), 16);
            return b1 + "." + b2 + "." + b3 + "." + b4;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String linuxType(String iface) {
        String name = key(iface);
        if (Files.isDirectory(Path.of("/sys/class/net", iface, "wireless"))) {
            return "wifi";
        }
        if ("lo".equals(name)) {
            return "loopback";
        }
        if (startsWithAny(name, "tun", "tap", "wg", "ppp")) {
            return "vpn";
        }
        if (startsWithAny(name, "br-", "virbr")) {
            return "bridge";
        }
        if (startsWithAny(name, "veth", "docker", "vmnet", "vboxnet")) {
            return "virtual";
        }
        if (startsWithAny(name, "eth", "enp", "eno", "ens", "em")) {
            return "ethernet";
        }
        return "unknown";
    }

    private static String windowsType(String adapterType, String name) {
        String text = (clean(adapterType) + " " + clean(name)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "wireless", "wi-fi", "wifi", "802.11")) {
            return "wifi";
        }
        if (containsAny(text, "vpn", "wireguard", "tailscale", "zerotier", "tap", "tun")) {
            return "vpn";
        }
        if (containsAny(text, "virtual", "hyper-v", "vmware", "virtualbox", "wsl")) {
            return "virtual";
        }
        if (containsAny(text, "ethernet", "802.3")) {
            return "ethernet";
        }
        return "unknown";
    }

    private static String macHardwarePortType(String hardwarePort) {
        String text = key(hardwarePort);
        if (containsAny(text, "wi-fi", "wifi", "airport")) {
            return "wifi";
        }
        if (text.contains("ethernet") || text.contains("thunderbolt")) {
            return "ethernet";
        }
        if (containsAny(text, "vpn", "ppp")) {
            return "vpn";
        }
        return "unknown";
    }
}
