package scheduler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoggerUtil {
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    // Add a new log
    public static void log(String message) {
        String timestamped = "[" + System.currentTimeMillis() + "] " + message;
        logs.add(timestamped);
        System.out.println(timestamped); // optional: console output
    }

    // Get all logs
    public static List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    // Export to file
    public static void exportToFile(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (String log : logs) {
                writer.write(log);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to export logs: " + e.getMessage());
        }
    }

    // Optional: Clear logs
    public static void clearLogs() {
        logs.clear();
    }
}
