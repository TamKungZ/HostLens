package me.tamkungz.hostlens.demo;

import me.tamkungz.hostlens.HostLens;
import me.tamkungz.hostlens.HostSnapshot;

public final class HostLensDemo {
    private HostLensDemo() {
    }

    public static void main(String[] args) {
        HostSnapshot snapshot = HostLens.capture();
        System.out.println(snapshot.toPrettyString());

        if (args.length > 0 && "--json".equalsIgnoreCase(args[0])) {
            System.out.println(snapshot.toJson());
        }
    }
}
