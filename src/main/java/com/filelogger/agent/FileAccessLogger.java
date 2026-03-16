package com.filelogger.agent;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe logger that appends file access entries to /tmp/files.txt.
 * This class is injected into the bootstrap classloader so that advice
 * inlined into JDK classes can call it.
 */
public class FileAccessLogger {

    private static volatile String LOG_FILE = "/tmp/files.txt";
    private static volatile boolean ENABLED = !"false".equalsIgnoreCase(
            System.getenv("FILE_LOGGER_JAVAAGENT_ENABLED"));

    public static void setLogFile(String path) {
        LOG_FILE = path;
    }
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ReentrantLock LOCK = new ReentrantLock();

    // Guard against recursive logging (our own writes to the log file)
    private static final ThreadLocal<Boolean> IN_PROGRESS =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Per-thread cooldown: suppresses repeated identical operation+path pairs
    // within the cooldown window. Handles both fast constructor chaining (~ms)
    // and application-level polling (seconds/minutes).
    // Configurable via env var FILE_LOGGER_JAVAAGENT_COOLDOWN_MS, default 5000ms.
    private static final long COOLDOWN_MS = parseCooldown();
    private static final ThreadLocal<Map<String, Long>> SEEN =
            ThreadLocal.withInitial(HashMap::new);

    private static long parseCooldown() {
        String val = System.getenv("FILE_LOGGER_JAVAAGENT_COOLDOWN_MS");
        if (val != null) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 5000L;
    }

    public static void log(String operation, String path) {
        log(operation, path, "OK", null);
    }

    public static void log(String operation, String path, String status, Throwable thrown) {
        if (!ENABLED || IN_PROGRESS.get()) {
            return;
        }

        // Suppress duplicate operation+status+path within the cooldown window
        long now = System.currentTimeMillis();
        String key = operation + "\0" + status + "\0" + path;
        Map<String, Long> seen = SEEN.get();
        Long lastSeen = seen.get(key);
        if (lastSeen != null && (now - lastSeen) < COOLDOWN_MS) {
            return;
        }
        seen.put(key, now);

        IN_PROGRESS.set(Boolean.TRUE);
        try {
            String timestamp = LocalDateTime.now().format(FMT);
            String thread = Thread.currentThread().getName();
            String line;
            if (thrown != null) {
                String error = thrown.getClass().getName() + ": " + thrown.getMessage();
                line = String.format("[%s] [%s] %s %s -- %s -- %s",
                        timestamp, thread, operation, status, path, error);
            } else {
                line = String.format("[%s] [%s] %s %s -- %s",
                        timestamp, thread, operation, status, path);
            }

            LOCK.lock();
            try (PrintWriter pw = new PrintWriter(
                    new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
                pw.println(line);
            } catch (IOException e) {
                // Silently ignore - we cannot risk disrupting the host application
            } finally {
                LOCK.unlock();
            }
        } finally {
            IN_PROGRESS.set(Boolean.FALSE);
        }
    }
}
