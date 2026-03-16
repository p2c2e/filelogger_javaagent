package com.filelogger.agent;

import net.bytebuddy.asm.Advice;
import java.nio.file.Path;

/**
 * Advice that intercepts NIO Files read/write utility methods such as
 * Files.readAllBytes, Files.readAllLines, Files.readString,
 * Files.write, Files.writeString, Files.newInputStream, Files.newOutputStream,
 * Files.newBufferedReader, Files.newBufferedWriter, Files.copy, Files.move, Files.delete.
 */
public class FilesReadWriteAdvice {

    @Advice.OnMethodEnter
    public static String[] onEnter(@Advice.Origin String method,
                                   @Advice.AllArguments Object[] args) {
        try {
            if (args != null && args.length > 0 && args[0] instanceof Path) {
                String path = ((Path) args[0]).toAbsolutePath().toString();
                String op = "NIO_FILE_ACCESS";
                String methodLower = method.toLowerCase();
                if (methodLower.contains("write") || methodLower.contains("outputstream")
                        || methodLower.contains("bufferedwriter")) {
                    op = "NIO_WRITE";
                } else if (methodLower.contains("read") || methodLower.contains("inputstream")
                        || methodLower.contains("bufferedreader")) {
                    op = "NIO_READ";
                } else if (methodLower.contains("copy")) {
                    op = "NIO_COPY";
                } else if (methodLower.contains("move")) {
                    op = "NIO_MOVE";
                } else if (methodLower.contains("delete")) {
                    op = "NIO_DELETE";
                }
                return new String[]{ op, path };
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter String[] enterData,
                              @Advice.Thrown Throwable thrown) {
        try {
            if (enterData != null && enterData[1] != null) {
                String status = (thrown == null) ? "OK" : "FAILED";
                FileAccessLogger.log(enterData[0], enterData[1], status, thrown);
            }
        } catch (Throwable ignored) {
        }
    }
}
