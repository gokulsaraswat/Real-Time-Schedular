package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridLayout;

public class TaskFormPanel extends JPanel {
    private final JTextField nameField = new JTextField();
    private final JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(3_000L), Long.valueOf(100L), Long.valueOf(86_400_000L), Long.valueOf(100L)));
    private final JSpinner startOffsetSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(0L), Long.valueOf(0L), Long.valueOf(86_400_000L), Long.valueOf(100L)));
    private final JSpinner deadlineWindowSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(5_000L), Long.valueOf(100L), Long.valueOf(86_400_000L), Long.valueOf(100L)));
    private final JTextField tagsField = new JTextField();
    private final JSpinner recurrenceSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(0L), Long.valueOf(0L), Long.valueOf(86_400_000L), Long.valueOf(100L)));
    private final JSpinner retryCountSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
    private final JSpinner retryBackoffSpinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(0L), Long.valueOf(0L), Long.valueOf(86_400_000L), Long.valueOf(100L)));

    public TaskFormPanel(String title) {
        setLayout(new GridLayout(0, 4, 8, 8));
        setBorder(BorderFactory.createTitledBorder(title));

        add(new JLabel("Task Name"));
        add(nameField);
        add(new JLabel("Priority (1 = highest)"));
        add(prioritySpinner);

        add(new JLabel("Duration (ms)"));
        add(durationSpinner);
        add(new JLabel("Start offset (ms)"));
        add(startOffsetSpinner);

        add(new JLabel("Deadline window (ms)"));
        add(deadlineWindowSpinner);
        add(new JLabel("Tags (comma separated)"));
        add(tagsField);

        add(new JLabel("Repeat every (ms)"));
        add(recurrenceSpinner);
        add(new JLabel("Max retries"));
        add(retryCountSpinner);

        add(new JLabel("Retry backoff (ms)"));
        add(retryBackoffSpinner);
        add(new JLabel("Notes"));
        add(new JLabel("Repeat 0 disables recurrence. Retries apply to missed deadlines."));
    }

    public TaskRequest toTaskRequest() {
        String name = nameField.getText().trim();
        int priority = (int) prioritySpinner.getValue();
        long durationMs = ((Number) durationSpinner.getValue()).longValue();
        long startOffsetMs = ((Number) startOffsetSpinner.getValue()).longValue();
        long deadlineWindowMs = ((Number) deadlineWindowSpinner.getValue()).longValue();
        String tags = tagsField.getText().trim();
        long recurrenceMs = ((Number) recurrenceSpinner.getValue()).longValue();
        int maxRetries = (int) retryCountSpinner.getValue();
        long retryBackoffMs = ((Number) retryBackoffSpinner.getValue()).longValue();

        long now = System.currentTimeMillis();
        long scheduledStartAt = now + Math.max(0L, startOffsetMs);
        long deadlineEpochMs = scheduledStartAt + Math.max(1L, deadlineWindowMs);

        return new TaskRequest(
                name,
                priority,
                durationMs,
                scheduledStartAt,
                deadlineEpochMs,
                tags,
                recurrenceMs,
                maxRetries,
                retryBackoffMs
        );
    }

    public void loadFromTask(ScheduledTask task) {
        long now = System.currentTimeMillis();
        nameField.setText(task.getName());
        prioritySpinner.setValue(task.getPriority());
        durationSpinner.setValue(task.getTotalDurationMs());
        startOffsetSpinner.setValue(Math.max(0L, task.getReadyAtEpochMs() - now));
        deadlineWindowSpinner.setValue(Math.max(100L, task.getDeadlineOffsetMs()));
        tagsField.setText(String.join(",", task.getTags()));
        recurrenceSpinner.setValue(task.getRecurrenceIntervalMs());
        retryCountSpinner.setValue(0);
        retryBackoffSpinner.setValue(0L);
    }

    public void resetForNewTask() {
        nameField.setText("");
        prioritySpinner.setValue(5);
        durationSpinner.setValue(3_000L);
        startOffsetSpinner.setValue(0L);
        deadlineWindowSpinner.setValue(5_000L);
        tagsField.setText("");
        recurrenceSpinner.setValue(0L);
        retryCountSpinner.setValue(0);
        retryBackoffSpinner.setValue(0L);
    }
}
