package com.filelogger.agent;

import net.bytebuddy.asm.Advice;
import java.io.File;

/**
 * Advice that intercepts RandomAccessFile constructors to log file access.
 * Constructor advice cannot use onThrowable (JVM verifier restriction),
 * so we log on enter only.
 */
public class RandomAccessFileAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.AllArguments Object[] args) {
        try {
            String path = null;
            String mode = "READ_WRITE";
            if (args != null && args.length >= 2) {
                Object first = args[0];
                if (first instanceof String) {
                    path = (String) first;
                } else if (first instanceof File) {
                    path = ((File) first).getAbsolutePath();
                }
                if (args[1] instanceof String) {
                    String m = (String) args[1];
                    mode = m.contains("w") ? "READ_WRITE" : "READ";
                }
            }
            if (path != null) {
                FileAccessLogger.log(mode, path);
            }
        } catch (Throwable ignored) {
        }
    }
}
