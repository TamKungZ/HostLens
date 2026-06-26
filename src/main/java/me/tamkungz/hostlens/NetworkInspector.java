package me.tamkungz.hostlens;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        List<NetworkInterfaceInfo> networks = new ArrayList<>();
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            networks.add(new NetworkInterfaceInfo(
                    networkInterface.getName(),
                    networkInterface.getDisplayName(),
                    macAddress(networkInterface.getHardwareAddress()),
                    networkInterface.isUp(),
                    networkInterface.isLoopback(),
                    networkInterface.isVirtual(),
                    networkInterface.getMTU(),
                    addresses(networkInterface)
            ));
        }
        snapshot.networks(networks);
    }

    private static List<String> addresses(NetworkInterface networkInterface) {
        List<String> result = new ArrayList<>();
        for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
            String hostAddress = address.getHostAddress();
            int zoneIndex = hostAddress.indexOf('%');
            result.add(zoneIndex >= 0 ? hostAddress.substring(0, zoneIndex) : hostAddress);
        }
        return result;
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
            builder.append(String.format("%02X", bytes[i]));
        }
        return builder.toString();
    }
}
