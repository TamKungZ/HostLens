package me.tamkungz.hostlens;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DiskInspector implements HostInspector {

    @Override
    public String name() {
        return "disk";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeDisk();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) throws IOException {
        List<DiskInfo> disks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                FileStore store = Files.getFileStore(root);
                addStore(disks, seen, store, root.toString());
            } catch (IOException ignored) {
                // Continue scanning other roots.
            }
        }

        if (disks.isEmpty()) {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                addStore(disks, seen, store, store.toString());
            }
        }

        snapshot.disks(disks);
    }

    private static void addStore(List<DiskInfo> disks, Set<String> seen, FileStore store, String mount) throws IOException {
        String key = store.name() + "|" + store.type() + "|" + mount;
        if (!seen.add(key)) {
            return;
        }
        disks.add(new DiskInfo(
                store.name(),
                mount,
                store.toString(),
                store.type(),
                store.getTotalSpace(),
                store.getUsableSpace(),
                store.getUnallocatedSpace(),
                store.isReadOnly()
        ));
    }
}
