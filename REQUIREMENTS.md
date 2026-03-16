In Java, is there any to log any file access (read / write etc.) and folder listings - without code changes? Preferably all the filenames being logged into a new /tmp/files.txt

ava Agent (most control, still zero app code changes)
Create a tiny agent JAR that intercepts java.io.FileInputStream, FileOutputStream, Files.list(), etc. using java.lang.instrument + Byte Buddy. Then attach with:


-javaagent:/path/to/file-logger-agent.jar

Help me create a javaagent that will do this.
