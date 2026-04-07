package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TaskDetailsPanel extends JPanel {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JTextArea detailsArea = new JTextArea();

    public TaskDetailsPanel() {
        this("Selected Task Details");
    }

    public TaskDetailsPanel(String title) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(title));
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        add(new JScrollPane(detailsArea), BorderLayout.CENTER);
    }

    public void showTask(ScheduledTask task, SchedulerPolicySettings settings, long now) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;

        if (task == null) {
            detailsArea.setText("Select a task to inspect metadata, recurrence, retry policy, timing, and scheduling signals.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Name: ").append(task.getName()).append(System.lineSeparator());
        builder.append("ID: ").append(task.getId()).append(System.lineSeparator());
        builder.append("Series: ").append(task.getSeriesId()).append(System.lineSeparator());
        builder.append("Occurrence: ").append(task.getOccurrenceIndex()).append("/").append(task.getRecurrenceLimit()).append(System.lineSeparator());
        builder.append("Status: ").append(task.getStatus()).append(System.lineSeparator());
        builder.append("Category: ").append(task.getCategory().isBlank() ? "-" : task.getCategory()).append(System.lineSeparator());
        builder.append("Tags: ").append(task.getTagsDisplay()).append(System.lineSeparator());
        builder.append("Priority: ").append(task.getPriority()).append(System.lineSeparator());
        builder.append("Effective Priority Now: ").append(task.getEffectivePriority(safeSettings, now)).append(System.lineSeparator());
        builder.append("At Risk: ").append(task.isAtRisk(safeSettings, now) ? "Yes" : "No").append(System.lineSeparator());
        builder.append("Slack: ").append(task.getSlackMs(now)).append(" ms").append(System.lineSeparator());
        builder.append("Queue Wait: ").append(task.getQueueWaitMs(now)).append(" ms").append(System.lineSeparator());
        builder.append("Created: ").append(formatTimestamp(task.getCreatedAt())).append(System.lineSeparator());
        builder.append("Ready At: ").append(formatTimestamp(task.getReadyAtEpochMs())).append(System.lineSeparator());
        builder.append("Deadline: ").append(formatTimestamp(task.getDeadlineEpochMs())).append(System.lineSeparator());
        builder.append("First Started: ").append(formatTimestamp(task.getFirstStartedAt())).append(System.lineSeparator());
        builder.append("Finished: ").append(formatTimestamp(task.getFinishedAt())).append(System.lineSeparator());
        builder.append("Total Duration: ").append(task.getTotalDurationMs()).append(" ms").append(System.lineSeparator());
        builder.append("Executed: ").append(task.getExecutedDurationMs()).append(" ms").append(System.lineSeparator());
        builder.append("Remaining: ").append(task.getRemainingDurationMs()).append(" ms").append(System.lineSeparator());
        builder.append("Progress: ").append(task.getProgressPercent()).append("%").append(System.lineSeparator());
        builder.append("Preemptions: ").append(task.getPreemptionCount()).append(System.lineSeparator());
        builder.append("Repeat: ").append(task.getRecurrenceSummary()).append(System.lineSeparator());
        builder.append("Retry Policy: ").append(task.getRetrySummary()).append(System.lineSeparator());
        builder.append("Retry Parent: ").append(task.getRetryParentTaskId().isBlank() ? "-" : task.getRetryParentTaskId()).append(System.lineSeparator());
        builder.append("Notes: ").append(task.getNotes().isBlank() ? "-" : task.getNotes()).append(System.lineSeparator());

        detailsArea.setText(builder.toString());
        detailsArea.setCaretPosition(0);
    }

    private String formatTimestamp(long epochMs) {
        if (epochMs <= 0L) {
            return "-";
        }
        return FORMATTER.format(Instant.ofEpochMilli(epochMs));
    }
}
