package scheduler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SchedulerStateStore {
    private final Path dataDirectory;
    private final Path stateFile;
    private final Path settingsFile;

    public SchedulerStateStore() {
        this(Path.of(System.getProperty("user.home"), ".real-time-scheduler"));
    }

    public SchedulerStateStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.stateFile = dataDirectory.resolve("tasks.ser");
        this.settingsFile = dataDirectory.resolve("scheduler-settings.ser");
    }

    public synchronized void saveTasks(Collection<ScheduledTask> tasks) {
        try {
            Files.createDirectories(dataDirectory);
            Path tempFile = stateFile.resolveSibling("tasks.tmp");
            List<ScheduledTask> snapshot = new ArrayList<>();
            for (ScheduledTask task : tasks) {
                snapshot.add(task.copy());
            }
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tempFile))) {
                out.writeObject(snapshot);
            }
            try {
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("Failed to persist scheduler state: " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<ScheduledTask> loadTasks() {
        if (!Files.exists(stateFile)) {
            return new ArrayList<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(stateFile))) {
            Object state = in.readObject();
            if (state instanceof List<?>) {
                return (List<ScheduledTask>) state;
            }
        } catch (Exception exception) {
            System.err.println("Failed to load scheduler state: " + exception.getMessage());
        }

        return new ArrayList<>();
    }

    public synchronized void savePolicySettings(SchedulerPolicySettings settings) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;
        try {
            Files.createDirectories(dataDirectory);
            Path tempFile = settingsFile.resolveSibling("scheduler-settings.tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tempFile))) {
                out.writeObject(safeSettings);
            }
            try {
                Files.move(tempFile, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveException) {
                Files.move(tempFile, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("Failed to persist scheduler policy settings: " + exception.getMessage());
        }
    }

    public synchronized SchedulerPolicySettings loadPolicySettings() {
        if (!Files.exists(settingsFile)) {
            return SchedulerPolicySettings.defaults();
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(settingsFile))) {
            Object state = in.readObject();
            if (state instanceof SchedulerPolicySettings settings) {
                return settings;
            }
        } catch (Exception exception) {
            System.err.println("Failed to load scheduler policy settings: " + exception.getMessage());
        }

        return SchedulerPolicySettings.defaults();
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getStateFile() {
        return stateFile;
    }

    public Path getSettingsFile() {
        return settingsFile;
    }
}
