# HostLens

[![](https://jitpack.io/v/TamKungZ/HostLens.svg)](https://jitpack.io/#TamKungZ/HostLens)

HostLens is a small Java 17 hardware / host information capture library.
It uses only the JDK, so it does not require external dependencies.

## Features

- Runtime / JVM information
- Operating system information
- CPU information
- Memory information
- GPU information, best-effort through OS commands
- Disk / file store information
- Network interface information
- Error collection per inspector instead of crashing by default
- Pretty text output and compact JSON output

## Usage

```java
import me.tamkungz.hostlens.HostLens;
import me.tamkungz.hostlens.HostSnapshot;

public class Main {
    public static void main(String[] args) {
        HostSnapshot snapshot = HostLens.capture();
        System.out.println(snapshot.toPrettyString());
        System.out.println(snapshot.toJson());
    }
}
```

Quick capture without GPU, disk, and network scans:

```java
HostSnapshot snapshot = HostLens.quickCapture();
```

Custom capture:

```java
HostSnapshot snapshot = HostLens.builder()
        .includeCpu(true)
        .includeMemory(true)
        .includeGpu(false)
        .includeDisk(true)
        .includeNetwork(true)
        .failFast(false)
        .capture();
```

## Run demo

```bash
./gradlew run
```

Or compile without Gradle:

```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out me.tamkungz.hostlens.demo.HostLensDemo
```

## Notes

GPU capture is best-effort because the JDK does not provide a standard GPU API.
HostLens tries platform commands when available:

- Windows: PowerShell `Get-CimInstance Win32_VideoController`, fallback `wmic`
- Linux: `lspci`, fallback `glxinfo`
- macOS: `system_profiler SPDisplaysDataType`
