# File Logger Java Agent

A zero-code-change Java agent that intercepts file I/O and directory listing operations, logging them to `/tmp/files.txt`.

## What it does

Instruments the following JDK classes using Byte Buddy advice (bytecode inlining):

| Class | Operations logged |
|---|---|
| `java.io.FileInputStream` | File reads (constructor) |
| `java.io.FileOutputStream` | File writes (constructor) |
| `java.io.RandomAccessFile` | File read/write (constructor) |
| `java.nio.file.Files` | `list`, `walk`, `newDirectoryStream`, `readAllBytes`, `readAllLines`, `readString`, `write`, `writeString`, `newInputStream`, `newOutputStream`, `newBufferedReader`, `newBufferedWriter`, `copy`, `move`, `delete`, `deleteIfExists` |

## Log format

Each line in `/tmp/files.txt`:

```
[timestamp] [thread-name] OPERATION -- /absolute/path/to/file
```

Operations: `READ`, `WRITE`, `READ_WRITE`, `LIST_DIR`, `NIO_READ`, `NIO_WRITE`, `NIO_COPY`, `NIO_MOVE`, `NIO_DELETE`, `NIO_FILE_ACCESS`

## Build

Requires Java 21+ and Maven 3.x.

```bash
mvn clean package
```

Produces: `target/file-logger-javaagent-1.0.0.jar`

## Usage

Attach to any Java application via the `-javaagent` flag:

```bash
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar -jar your-app.jar
```

Or with a class:

```bash
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar -cp your-classpath com.example.Main
```

To log to a custom file (absolute path), pass it as the agent argument:

```bash
java -javaagent:/path/to/file-logger-javaagent-1.0.0.jar=/var/log/my-app-files.txt -jar your-app.jar
```

If no argument is given, defaults to `/tmp/files.txt`. The log file is opened in append mode.

## Quick start (setup.sh)

The `setup.sh` script builds the agent, compiles the demo, and runs it in one go:

```bash
# Default log to /tmp/files.txt
./setup.sh

# Custom log file
./setup.sh /tmp/my-custom-log.txt
```

It runs `demo/SampleApp.java`, which exercises every intercepted operation:
- FileOutputStream write + append
- FileInputStream read
- RandomAccessFile read-only and read-write modes
- NIO Files read utilities (readAllBytes, readAllLines, readString)
- NIO Files write utilities (writeString, write)
- NIO Files copy and move
- NIO Files directory listing (list, walk)
- NIO Files delete and deleteIfExists
- Multi-threaded writes (two worker threads)

## Design notes

- Thread-safe logging with `ReentrantLock`
- Recursive-call guard via `ThreadLocal` prevents infinite loops when the agent's own log writes trigger instrumented code
- Only `FileAccessLogger` is injected into the bootstrap classloader (extracted into a temp JAR at startup) to avoid classloader conflicts with the shaded Byte Buddy dependency
- All advice classes use `@Advice.OnMethodEnter` so they fire before the actual I/O operation
- Exceptions in advice are silently caught to never disrupt the host application
