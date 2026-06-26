package me.tamkungz.hostlens;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class HostLensSupport {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(3);

    private HostLensSupport() {
    }

    static String property(String key) {
        return System.getProperty(key, "unknown");
    }

    static String osNameLower() {
        return property("os.name").toLowerCase(Locale.ROOT);
    }

    static boolean isWindows() {
        return osNameLower().contains("win");
    }

    static boolean isLinux() {
        return osNameLower().contains("linux");
    }

    static boolean isMac() {
        String os = osNameLower();
        return os.contains("mac") || os.contains("darwin");
    }

    static String osFamily() {
        if (isWindows()) {
            return "windows";
        }
        if (isLinux()) {
            return "linux";
        }
        if (isMac()) {
            return "macos";
        }
        return "unknown";
    }

    static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String env = System.getenv("HOSTNAME");
            return isBlank(env) ? "unknown" : env;
        }
    }

    static List<String> command(String... command) {
        List<String> lines = new ArrayList<>();
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return List.of();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String clean = clean(line);
                    if (!clean.isEmpty()) {
                        lines.add(clean);
                    }
                }
            }
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return lines;
    }

    static Optional<String> commandFirstLine(String... command) {
        return command(command).stream().filter(line -> !line.isBlank()).findFirst();
    }

    static Optional<String> firstExistingReadableLine(Path path) {
        if (!Files.isReadable(path)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = clean(line);
                if (!clean.isEmpty()) {
                    return Optional.of(clean);
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static List<String> readAllLines(Path path) {
        if (!Files.isReadable(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(HostLensSupport::clean)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
