package com.filelogger.agent;

import net.bytebuddy.asm.Advice;
import java.nio.file.Path;

/**
 * Advice that intercepts Files.list() / Files.walk() / Files.newDirectoryStream()
 * to log directory listing operations.
 */
public class FilesListAdvice {

    @Advice.OnMethodEnter
    public static String onEnter(@Advice.AllArguments Object[] args) {
        try {
            if (args != null && args.length > 0 && args[0] instanceof Path) {
                return ((Path) args[0]).toAbsolutePath().toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter String path,
                              @Advice.Thrown Throwable thrown) {
        try {
            if (path != null) {
                String status = (thrown == null) ? "OK" : "FAILED";
                FileAccessLogger.log("LIST_DIR", path, status, thrown);
            }
        } catch (Throwable ignored) {
        }
    }
}
