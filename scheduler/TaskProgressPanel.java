package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TaskProgressPanel extends JPanel {
    private static final DateTimeFormatter DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JLabel taskLabel = new JLabel("No task running");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel detailLabel = new JLabel(" ");

    public TaskProgressPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Current Task"));
        progressBar.setStringPainted(true);
        add(taskLabel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
        add(detailLabel, BorderLayout.SOUTH);
    }

    public void updateCurrentTask(ScheduledTask task, SchedulerPolicySettings settings, long now) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;

        if (task == null) {
            clearProgress();
            return;
        }

        String taskTitle = task.getName();
        if (!task.getCategory().isBlank()) {
            taskTitle += " [" + task.getCategory() + "]";
        }
        taskLabel.setText("Running: " + taskTitle);
        progressBar.setValue(task.getProgressPercent());
        progressBar.setString(task.getProgressPercent() + "%");

        String riskLabel = task.isAtRisk(safeSettings, now) ? "AT RISK" : "Stable";
        detailLabel.setText(
                safeSettings.schedulingMode()
                        + " • Priority " + task.getPriority()
                        + " → effective " + task.getEffectivePriority(safeSettings, now)
                        + " • Remaining " + task.getRemainingDurationMs() + " ms"
                        + " • Slack " + task.getSlackMs(now) + " ms"
                        + " • " + riskLabel
                        + " • Deadline " + DEADLINE_FORMATTER.format(Instant.ofEpochMilli(task.getDeadlineEpochMs()))
        );
    }

    public void clearProgress() {
        taskLabel.setText("No task running");
        progressBar.setValue(0);
        progressBar.setString("0%");
        detailLabel.setText("Scheduler is idle.");
    }
}
