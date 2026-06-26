package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLensMachineReportTest {

    @Test
    void printAndWriteCurrentMachineSnapshot(TestReporter reporter) throws IOException {
        HostSnapshot snapshot = assertDoesNotThrow(HostLens::capture);

        assertNotNull(snapshot);
        assertNotNull(snapshot.capturedAt());
        assertNotNull(snapshot.runtime());
        assertNotNull(snapshot.operatingSystem());

        String pretty = snapshot.toPrettyString();
        String json = snapshot.toJson();

        assertFalse(pretty.isBlank());
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));

        Path reportDir = Path.of(System.getProperty("hostlens.report.dir", "build/hostlens"));
        Files.createDirectories(reportDir);

        Path prettyReport = reportDir.resolve("hostlens-machine-snapshot.txt");
        Path jsonReport = reportDir.resolve("hostlens-machine-snapshot.json");

        Files.writeString(prettyReport, pretty, StandardCharsets.UTF_8);
        Files.writeString(jsonReport, json, StandardCharsets.UTF_8);

        reporter.publishEntry(Map.of(
                "capturedAt", DateTimeFormatter.ISO_INSTANT.format(snapshot.capturedAt()),
                "prettyReport", prettyReport.toAbsolutePath().toString(),
                "jsonReport", jsonReport.toAbsolutePath().toString()
        ));

        System.out.println();
        System.out.println("========== HostLens current machine snapshot ==========");
        System.out.print(pretty);
        System.out.println("========== HostLens report files ==========");
        System.out.println("Pretty: " + prettyReport.toAbsolutePath());
        System.out.println("JSON  : " + jsonReport.toAbsolutePath());
        System.out.println("=======================================================");
    }
}
