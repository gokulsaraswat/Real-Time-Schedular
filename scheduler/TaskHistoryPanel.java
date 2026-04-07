package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TaskHistoryPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JTextArea historyArea = new JTextArea();

    public TaskHistoryPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Task Details & History"));

        historyArea.setEditable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        add(new JScrollPane(historyArea), BorderLayout.CENTER);

        clear();
    }

    public void showTask(ScheduledTask task) {
        if (task == null) {
            clear();
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Name: ").append(task.getName()).append(System.lineSeparator());
        builder.append("Status: ").append(task.getStatus()).append(System.lineSeparator());
        builder.append("Priority: ").append(task.getPriority()).append(System.lineSeparator());
        builder.append("Run: ").append(task.getOccurrenceIndex()).append("/").append(task.getRecurrenceLimit())
                .append(System.lineSeparator());
        builder.append("Series: ").append(shortId(task.getSeriesId())).append(System.lineSeparator());
        // Parent task concept is introduced in later patches; keep panel compatible with Patch 2.
        String tagText = task.getTags().isEmpty() ? "-" : String.join(",", task.getTags());
        builder.append("Tags: ").append(tagText).append(System.lineSeparator());
        builder.append("Scheduled start: ").append(formatTime(task.getReadyAtEpochMs())).append(System.lineSeparator());
        builder.append("Deadline: ").append(formatTime(task.getDeadlineEpochMs())).append(System.lineSeparator());
        builder.append("Deadline window: ").append(task.getDeadlineOffsetMs()).append(" ms").append(System.lineSeparator());
        builder.append("Duration: ").append(task.getTotalDurationMs()).append(" ms").append(System.lineSeparator());
        builder.append("Remaining: ").append(task.getRemainingDurationMs()).append(" ms").append(System.lineSeparator());
        builder.append("Progress: ").append(task.getProgressPercent()).append("%").append(System.lineSeparator());
        builder.append("Repeat every: ").append(task.getRecurrenceIntervalMs()).append(" ms").append(System.lineSeparator());
        builder.append("Retry policy: ").append("-").append(System.lineSeparator());
        builder.append("Preemptions: ").append(task.getPreemptionCount()).append(System.lineSeparator());
        builder.append("Created: ").append(formatTime(task.getCreatedAt())).append(System.lineSeparator());
        if (task.getFirstStartedAt() > 0L) {
            builder.append("First started: ").append(formatTime(task.getFirstStartedAt())).append(System.lineSeparator());
        }
        if (task.getFinishedAt() > 0L) {
            builder.append("Finished: ").append(formatTime(task.getFinishedAt())).append(System.lineSeparator());
        }

        builder.append(System.lineSeparator()).append("History").append(System.lineSeparator());
        builder.append("-------").append(System.lineSeparator());

        // Event history is introduced in later patches.

        historyArea.setText(builder.toString());
        historyArea.setCaretPosition(0);
    }

    public void clear() {
        historyArea.setText("Select a task to inspect its details, lifecycle history, recurrence, and retry information.");
        historyArea.setCaretPosition(0);
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String formatTime(long epochMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs));
    }
}
