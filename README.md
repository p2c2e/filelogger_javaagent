# File Logger Java Agent

A zero-code-change Java agent that intercepts file I/O and directory listing operations in any Java application and logs them to a file. Uses Byte Buddy bytecode instrumentation to inline advice directly into JDK classes at runtime.

## Intercepted Operations

| Class | Operations | Log Label |
|---|---|---|
| `java.io.FileInputStream` | Constructor (file reads) | `READ` |
| `java.io.FileOutputStream` | Constructor (file writes) | `WRITE` |
| `java.io.RandomAccessFile` | Constructor (read or read-write) | `READ` / `READ_WRITE` |
| `java.nio.file.Files` | `list`, `walk`, `newDirectoryStream` | `LIST_DIR` |
| `java.nio.file.Files` | `readAllBytes`, `readAllLines`, `readString`, `newInputStream`, `newBufferedReader` | `NIO_READ` |
| `java.nio.file.Files` | `write`, `writeString`, `newOutputStream`, `newBufferedWriter` | `NIO_WRITE` |
| `java.nio.file.Files` | `copy` | `NIO_COPY` |
| `java.nio.file.Files` | `move` | `NIO_MOVE` |
| `java.nio.file.Files` | `delete`, `deleteIfExists` | `NIO_DELETE` |

## Prerequisites

- Java 21+
- Maven 3.x

## Building

```bash
mvn clean package
```

This produces the shaded uber-JAR at:

```
target/file-logger-javaagent-1.0.0.jar
```

Byte Buddy is relocated to `com.filelogger.shaded.bytebuddy` to avoid classloader conflicts with the target application.

## Usage

Attach the agent to any Java application with the `-javaagent` JVM flag:

```bash
# With a JAR
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar -jar your-app.jar

# With a classpath
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar -cp your-classpath com.example.Main
```

To specify a custom log file path, pass it as the agent argument (separated by `=`):

```bash
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar=/var/log/my-app-files.txt -jar your-app.jar
```

The log file path is automatically prefixed with the JVM process ID (e.g., `/var/log/12345-my-app-files.txt`).

Default log file: `/tmp/files.txt` (becomes `/tmp/<PID>-files.txt`).

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `FILE_LOGGER_JAVAAGENT_ENABLED` | `true` | Set to `false` to disable all logging. Any other value (or unset) keeps logging enabled. |
| `FILE_LOGGER_JAVAAGENT_COOLDOWN_MS` | `5000` | Deduplication window in milliseconds. Repeated identical operation + status + path entries within this window are suppressed. Set to `0` to log every access. |

Example:

```bash
FILE_LOGGER_JAVAAGENT_ENABLED=true \
FILE_LOGGER_JAVAAGENT_COOLDOWN_MS=2000 \
  java -javaagent:target/file-logger-javaagent-1.0.0.jar -jar your-app.jar
```

## Log Format

Each line written to the log file:

```
[yyyy-MM-dd HH:mm:ss.SSS] [thread-name] OPERATION STATUS -- /absolute/path
```

On failure, the exception is appended:

```
[yyyy-MM-dd HH:mm:ss.SSS] [thread-name] OPERATION FAILED -- /absolute/path -- ExceptionClass: message
```

Example output:

```
[2026-03-16 10:30:45.123] [main] READ OK -- /tmp/demo/classic.txt
[2026-03-16 10:30:45.234] [main] WRITE OK -- /tmp/demo/classic.txt
[2026-03-16 10:30:45.345] [main] LIST_DIR OK -- /tmp/demo
[2026-03-16 10:30:45.456] [worker-1] NIO_WRITE OK -- /tmp/demo/thread-1.txt
[2026-03-16 10:30:45.567] [main] NIO_READ FAILED -- /tmp/no-such-file.txt -- java.nio.file.NoSuchFileException: /tmp/no-such-file.txt
```

## Quick Start with setup.sh

The included `setup.sh` script builds the agent, compiles the demo app, and runs it:

```bash
# Default log location (/tmp/files.txt)
./setup.sh

# Custom log location
./setup.sh /tmp/my-custom-log.txt
```

The demo (`demo/SampleApp.java`) exercises all intercepted operations including multi-threaded writes and intentional failures.
