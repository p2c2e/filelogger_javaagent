package com.filelogger.agent;

import net.bytebuddy.asm.Advice;
import java.io.File;

/**
 * Advice that intercepts FileInputStream constructors to log file reads.
 * Constructor advice cannot use onThrowable (JVM verifier restriction),
 * so we log on enter only. NIO-level failures are captured by FilesReadWriteAdvice.
 */
public class FileInputStreamAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.AllArguments Object[] args) {
        try {
            String path = null;
            if (args != null && args.length > 0) {
                Object first = args[0];
                if (first instanceof String) {
                    path = (String) first;
                } else if (first instanceof File) {
                    path = ((File) first).getAbsolutePath();
                }
            }
            if (path != null) {
                FileAccessLogger.log("READ", path);
            }
        } catch (Throwable ignored) {
        }
    }
}
