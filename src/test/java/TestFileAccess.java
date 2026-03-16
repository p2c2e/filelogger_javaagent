import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Simple test program to verify the file-logger-agent intercepts I/O operations.
 * Run with:
 *   java -javaagent:target/file-logger-javaagent-1.0.0.jar -cp src/test/java TestFileAccess
 * Then check /tmp/files.txt for logged entries.
 */
public class TestFileAccess {

    public static void main(String[] args) throws Exception {
        Path tempFile = Path.of("/tmp/agent-test-file.txt");

        // 1. Write via FileOutputStream
        System.out.println("Writing via FileOutputStream...");
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            fos.write("Hello from agent test\n".getBytes());
        }

        // 2. Read via FileInputStream
        System.out.println("Reading via FileInputStream...");
        try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
            byte[] buf = new byte[1024];
            int n = fis.read(buf);
            System.out.println("  Read " + n + " bytes");
        }

        // 3. NIO Files.readAllBytes
        System.out.println("Reading via Files.readAllBytes...");
        byte[] content = Files.readAllBytes(tempFile);
        System.out.println("  Got " + content.length + " bytes");

        // 4. NIO Files.writeString
        System.out.println("Writing via Files.writeString...");
        Files.writeString(tempFile, "Updated by NIO\n");

        // 5. Directory listing via Files.list
        System.out.println("Listing /tmp via Files.list...");
        try (Stream<Path> entries = Files.list(Path.of("/tmp"))) {
            long count = entries.count();
            System.out.println("  Found " + count + " entries");
        }

        // 6. Cleanup
        Files.deleteIfExists(tempFile);

        System.out.println("\nDone. Check /tmp/files.txt for logged entries.");
    }
}
