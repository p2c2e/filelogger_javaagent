package com.filelogger.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Java agent entry point. Instruments JDK file I/O and directory listing
 * classes so every access is logged to /tmp/files.txt without any
 * application code changes.
 *
 * Usage:  java -javaagent:/path/to/file-logger-agent-1.0.0.jar  YourApp
 */
public class FileLoggerAgent {

    public static void premain(String args, Instrumentation instrumentation) {
        // Resolve log file path: env var > agent argument > default
        String envFile = System.getenv("FILE_LOGGER_JAVAAGENT_FILENAME");
        String logFile;
        if (envFile != null && !envFile.isBlank()) {
            logFile = envFile.trim();
        } else if (args != null && !args.isBlank()) {
            logFile = args.trim();
        } else {
            logFile = "/tmp/files.txt";
        }

        // Prefix the filename with the PID: /mypath/override.txt -> /mypath/<PID>-override.txt
        long pid = ProcessHandle.current().pid();
        int lastSep = logFile.lastIndexOf(File.separatorChar);
        if (lastSep >= 0) {
            logFile = logFile.substring(0, lastSep + 1) + pid + "-" + logFile.substring(lastSep + 1);
        } else {
            logFile = pid + "-" + logFile;
        }

        System.out.println("[FileLoggerAgent] Initializing - logging file access to " + logFile);

        // Extract FileAccessLogger into a small bootstrap JAR so that advice
        // inlined into JDK classes can resolve it at the bootstrap classloader.
        try {
            injectBootstrapClasses(instrumentation);
        } catch (Exception e) {
            System.err.println("[FileLoggerAgent] FATAL: Could not inject bootstrap classes: " + e);
            return;
        }

        // Configure the log file path (must happen after bootstrap injection
        // so that the bootstrap-loaded copy of FileAccessLogger receives the value)
        FileAccessLogger.setLogFile(logFile);

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "file-logger-agent");
        tempDir.mkdirs();

        AgentBuilder builder = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .ignore(ElementMatchers.none())
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName,
                                        ClassLoader classLoader,
                                        JavaModule module,
                                        boolean loaded,
                                        Throwable throwable) {
                        System.err.println("[FileLoggerAgent] ERROR transforming " + typeName
                                + ": " + throwable.getMessage());
                    }
                });

        // --- FileInputStream (constructors) ---
        builder = builder
                .type(ElementMatchers.named("java.io.FileInputStream"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return b.visit(Advice.to(FileInputStreamAdvice.class)
                                .on(ElementMatchers.isConstructor()));
                    }
                });

        // --- FileOutputStream (constructors) ---
        builder = builder
                .type(ElementMatchers.named("java.io.FileOutputStream"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return b.visit(Advice.to(FileOutputStreamAdvice.class)
                                .on(ElementMatchers.isConstructor()));
                    }
                });

        // --- RandomAccessFile (constructors) ---
        builder = builder
                .type(ElementMatchers.named("java.io.RandomAccessFile"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return b.visit(Advice.to(RandomAccessFileAdvice.class)
                                .on(ElementMatchers.isConstructor()));
                    }
                });

        // --- java.nio.file.Files: list / walk / newDirectoryStream + read/write/copy/move/delete ---
        builder = builder
                .type(ElementMatchers.named("java.nio.file.Files"))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> b,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return b
                                .visit(Advice.to(FilesListAdvice.class)
                                        .on(ElementMatchers.named("list")
                                                .or(ElementMatchers.named("walk"))
                                                .or(ElementMatchers.named("newDirectoryStream"))))
                                .visit(Advice.to(FilesReadWriteAdvice.class)
                                        .on(ElementMatchers.named("readAllBytes")
                                                .or(ElementMatchers.named("readAllLines"))
                                                .or(ElementMatchers.named("readString"))
                                                .or(ElementMatchers.named("write"))
                                                .or(ElementMatchers.named("writeString"))
                                                .or(ElementMatchers.named("newInputStream"))
                                                .or(ElementMatchers.named("newOutputStream"))
                                                .or(ElementMatchers.named("newBufferedReader"))
                                                .or(ElementMatchers.named("newBufferedWriter"))
                                                .or(ElementMatchers.named("copy"))
                                                .or(ElementMatchers.named("move"))
                                                .or(ElementMatchers.named("delete"))
                                                .or(ElementMatchers.named("deleteIfExists"))));
                    }
                });

        builder.installOn(instrumentation);

        System.out.println("[FileLoggerAgent] Instrumentation installed successfully");
    }

    /**
     * Extracts FileAccessLogger.class from the agent JAR into a tiny temp JAR
     * and appends it to the bootstrap classloader search path.
     * This avoids putting the entire shaded agent on bootstrap (which would
     * cause LinkageErrors with the app-classloader copy of Byte Buddy).
     */
    private static void injectBootstrapClasses(Instrumentation instrumentation) throws Exception {
        File agentJar = new File(
                FileLoggerAgent.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());

        // Classes that the inlined advice code references
        String[] classesToInject = {
                "com/filelogger/agent/FileAccessLogger.class"
        };

        File tempJar = File.createTempFile("file-logger-bootstrap-", ".jar");
        tempJar.deleteOnExit();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        try (JarFile source = new JarFile(agentJar);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar), manifest)) {
            for (String className : classesToInject) {
                JarEntry entry = source.getJarEntry(className);
                if (entry != null) {
                    jos.putNextEntry(new JarEntry(className));
                    try (InputStream is = source.getInputStream(entry)) {
                        is.transferTo(jos);
                    }
                    jos.closeEntry();
                } else {
                    System.err.println("[FileLoggerAgent] WARNING: " + className + " not found in agent JAR");
                }
            }
        }

        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(tempJar));
        System.out.println("[FileLoggerAgent] Injected bootstrap helper classes from: " + tempJar);
    }
}
