package scheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoggerUtil {
    private static final int MAX_IN_MEMORY_LOGS = 5000;
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static Path logFile;

    private LoggerUtil() {
    }

    public static synchronized void init(Path dataDirectory) {
        try {
            Files.createDirectories(dataDirectory);
            logFile = dataDirectory.resolve("scheduler.log");
            logs.clear();
            if (Files.exists(logFile)) {
                logs.addAll(Files.readAllLines(logFile, StandardCharsets.UTF_8));
                trimLogs();
            }
        } catch (IOException exception) {
            System.err.println("Failed to initialize logger: " + exception.getMessage());
        }
    }

    public static void log(String message) {
        String entry = "[" + LocalDateTime.now().format(FORMATTER) + "] " + message;

        synchronized (LoggerUtil.class) {
            logs.add(entry);
            trimLogs();

            if (logFile != null) {
                try {
                    Files.writeString(
                            logFile,
                            entry + System.lineSeparator(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } catch (IOException exception) {
                    System.err.println("Failed to write log entry: " + exception.getMessage());
                }
            }
        }

        System.out.println(entry);
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public static synchronized void exportToFile(Path targetFile) throws IOException {
        Files.write(targetFile, logs, StandardCharsets.UTF_8);
    }

    public static synchronized void clearInMemoryLogs() {
        logs.clear();
    }

    private static void trimLogs() {
        while (logs.size() > MAX_IN_MEMORY_LOGS) {
            logs.remove(0);
        }
    }
}
