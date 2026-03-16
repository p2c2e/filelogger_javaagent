import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sample application demonstrating every type of file I/O operation
 * that the file-logger-agent intercepts.
 *
 * Run via setup.sh or manually:
 *   java -javaagent:target/file-logger-agent-1.0.0.jar -cp demo SampleApp
 */
public class SampleApp {

    private static final Path WORK_DIR = Path.of("/tmp/file-logger-demo");

    public static void main(String[] args) throws Exception {
        System.out.println("=== File Logger Agent - Demo ===\n");

        // Prepare a clean workspace
        if (Files.exists(WORK_DIR)) {
            try (Stream<Path> walk = Files.walk(WORK_DIR)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
        Files.createDirectories(WORK_DIR);

        // -------------------------------------------------------
        // 1. Classic I/O: FileOutputStream + FileInputStream
        // -------------------------------------------------------
        section("1. Classic I/O - FileOutputStream write");
        Path classicFile = WORK_DIR.resolve("classic.txt");
        try (FileOutputStream fos = new FileOutputStream(classicFile.toFile())) {
            fos.write("Line 1: written via FileOutputStream\n".getBytes());
            fos.write("Line 2: still FileOutputStream\n".getBytes());
        }
        System.out.println("   Wrote " + Files.size(classicFile) + " bytes");

        section("1b. Classic I/O - FileInputStream read");
        try (FileInputStream fis = new FileInputStream(classicFile.toFile());
             BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("   > " + line);
            }
        }

        // -------------------------------------------------------
        // 2. Classic I/O: FileOutputStream in append mode
        // -------------------------------------------------------
        section("2. Classic I/O - FileOutputStream append");
        try (FileOutputStream fos = new FileOutputStream(classicFile.toFile(), true)) {
            fos.write("Line 3: appended via FileOutputStream\n".getBytes());
        }
        System.out.println("   Appended. New size: " + Files.size(classicFile) + " bytes");

        // -------------------------------------------------------
        // 3. RandomAccessFile (read + write)
        // -------------------------------------------------------
        section("3. RandomAccessFile - read/write mode");
        Path rafFile = WORK_DIR.resolve("random-access.dat");
        try (RandomAccessFile raf = new RandomAccessFile(rafFile.toFile(), "rw")) {
            raf.writeUTF("Hello from RandomAccessFile");
            raf.seek(0);
            String value = raf.readUTF();
            System.out.println("   RAF read back: " + value);
        }

        section("3b. RandomAccessFile - read-only mode");
        try (RandomAccessFile raf = new RandomAccessFile(rafFile.toFile(), "r")) {
            String value = raf.readUTF();
            System.out.println("   RAF read-only: " + value);
        }

        // -------------------------------------------------------
        // 4. NIO Files - read utilities
        // -------------------------------------------------------
        section("4. NIO Files.readAllBytes");
        byte[] raw = Files.readAllBytes(classicFile);
        System.out.println("   Read " + raw.length + " bytes");

        section("4b. NIO Files.readAllLines");
        List<String> lines = Files.readAllLines(classicFile);
        System.out.println("   Read " + lines.size() + " lines");

        section("4c. NIO Files.readString");
        String content = Files.readString(classicFile);
        System.out.println("   Read string of length " + content.length());

        // -------------------------------------------------------
        // 5. NIO Files - write utilities
        // -------------------------------------------------------
        section("5. NIO Files.writeString");
        Path nioFile = WORK_DIR.resolve("nio-written.txt");
        Files.writeString(nioFile, "Written via Files.writeString\n");
        System.out.println("   Created " + nioFile.getFileName());

        section("5b. NIO Files.write (bytes)");
        Path nioFile2 = WORK_DIR.resolve("nio-bytes.txt");
        Files.write(nioFile2, "Written via Files.write bytes\n".getBytes());
        System.out.println("   Created " + nioFile2.getFileName());

        // -------------------------------------------------------
        // 6. NIO Files - copy and move
        // -------------------------------------------------------
        section("6. NIO Files.copy");
        Path copied = WORK_DIR.resolve("copied.txt");
        Files.copy(nioFile, copied, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("   Copied " + nioFile.getFileName() + " -> " + copied.getFileName());

        section("6b. NIO Files.move");
        Path moved = WORK_DIR.resolve("moved.txt");
        Files.move(copied, moved, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("   Moved copied.txt -> moved.txt");

        // -------------------------------------------------------
        // 7. NIO Files - directory listing
        // -------------------------------------------------------
        section("7. NIO Files.list (directory listing)");
        try (Stream<Path> entries = Files.list(WORK_DIR)) {
            entries.forEach(p -> System.out.println("   - " + p.getFileName()));
        }

        section("7b. NIO Files.walk (recursive listing)");
        try (Stream<Path> entries = Files.walk(WORK_DIR)) {
            entries.forEach(p -> System.out.println("   - " + WORK_DIR.relativize(p)));
        }

        // -------------------------------------------------------
        // 8. NIO Files - delete
        // -------------------------------------------------------
        section("8. NIO Files.delete + deleteIfExists");
        Files.delete(moved);
        System.out.println("   Deleted moved.txt");
        boolean existed = Files.deleteIfExists(WORK_DIR.resolve("nonexistent.txt"));
        System.out.println("   deleteIfExists(nonexistent.txt) returned " + existed);

        // -------------------------------------------------------
        // 9. Multi-threaded file access
        // -------------------------------------------------------
        section("9. Multi-threaded writes");
        Thread t1 = new Thread(() -> {
            try {
                Path f = WORK_DIR.resolve("thread-1.txt");
                Files.writeString(f, "From thread-1\n");
                System.out.println("   [thread-1] wrote " + f.getFileName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "worker-1");

        Thread t2 = new Thread(() -> {
            try {
                Path f = WORK_DIR.resolve("thread-2.txt");
                Files.writeString(f, "From thread-2\n");
                System.out.println("   [thread-2] wrote " + f.getFileName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "worker-2");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // -------------------------------------------------------
        // 10. Failed operations (FileNotFoundException, NoSuchFileException, etc.)
        // -------------------------------------------------------
        section("10. FAILED - FileInputStream on non-existent file");
        try {
            new FileInputStream("/tmp/file-logger-demo/does-not-exist.txt").close();
        } catch (Exception e) {
            System.out.println("   Caught expected: " + e.getClass().getSimpleName());
        }

        section("10b. FAILED - FileOutputStream on invalid path");
        try {
            new FileOutputStream("/tmp/file-logger-demo/no-such-dir/fail.txt").close();
        } catch (Exception e) {
            System.out.println("   Caught expected: " + e.getClass().getSimpleName());
        }

        section("10c. FAILED - NIO Files.readAllBytes on non-existent file");
        try {
            Files.readAllBytes(Path.of("/tmp/file-logger-demo/ghost.txt"));
        } catch (Exception e) {
            System.out.println("   Caught expected: " + e.getClass().getSimpleName());
        }

        section("10d. FAILED - NIO Files.list on non-existent directory");
        try {
            Files.list(Path.of("/tmp/file-logger-demo/no-such-dir")).close();
        } catch (Exception e) {
            System.out.println("   Caught expected: " + e.getClass().getSimpleName());
        }

        section("10e. FAILED - NIO Files.delete on non-existent file");
        try {
            Files.delete(Path.of("/tmp/file-logger-demo/phantom.txt"));
        } catch (Exception e) {
            System.out.println("   Caught expected: " + e.getClass().getSimpleName());
        }

        // -------------------------------------------------------
        // Cleanup
        // -------------------------------------------------------
        section("Cleanup");
        try (Stream<Path> walk = Files.walk(WORK_DIR)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        System.out.println("   Removed " + WORK_DIR);

        System.out.println("\n=== Demo complete ===");
        System.out.println("Check the log file for all intercepted operations.");
    }

    private static void section(String title) {
        System.out.println("\n-- " + title);
    }
}
