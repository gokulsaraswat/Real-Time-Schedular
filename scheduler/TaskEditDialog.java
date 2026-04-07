package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

public class TaskEditDialog extends JDialog {
    private final ScheduledTask originalTask;
    private TaskConfiguration result;

    private final JTextField nameField = new JTextField();
    private final JTextField categoryField = new JTextField();
    private final JTextField tagsField = new JTextField();
    private final JTextField notesField = new JTextField();

    private final JSpinner prioritySpinner = new JSpinner();
    private final JSpinner durationSpinner = new JSpinner();
    private final JSpinner deadlineOffsetSpinner = new JSpinner();
    private final JSpinner recurrenceIntervalSpinner = new JSpinner();
    private final JSpinner occurrencesSpinner = new JSpinner();
    private final JSpinner retryLimitSpinner = new JSpinner();
    private final JSpinner retryBackoffSpinner = new JSpinner();

    public static TaskConfiguration showDialog(JFrame owner, ScheduledTask task) {
        TaskEditDialog dialog = new TaskEditDialog(owner, task);
        dialog.setVisible(true);
        return dialog.result;
    }

    private TaskEditDialog(JFrame owner, ScheduledTask task) {
        super(owner, "Edit Task", true);
        this.originalTask = task;

        long now = System.currentTimeMillis();
        long deadlineOffset = Math.max(500L, task.getDeadlineEpochMs() - now);
        int minimumOccurrences = Math.max(1, task.getOccurrenceIndex());

        prioritySpinner.setModel(new SpinnerNumberModel(task.getPriority(), 1, 10, 1));
        durationSpinner.setModel(new SpinnerNumberModel(task.getTotalDurationMs(), 100L, 600_000L, 100L));
        deadlineOffsetSpinner.setModel(new SpinnerNumberModel(deadlineOffset, 500L, 86_400_000L, 500L));
        recurrenceIntervalSpinner.setModel(new SpinnerNumberModel(task.getRecurrenceIntervalMs(), 0L, 86_400_000L, 500L));
        occurrencesSpinner.setModel(new SpinnerNumberModel(Math.max(task.getRecurrenceLimit(), minimumOccurrences), minimumOccurrences, 1000, 1));
        retryLimitSpinner.setModel(new SpinnerNumberModel(task.getRetryLimit(), task.getRetryAttempt(), 10, 1));
        retryBackoffSpinner.setModel(new SpinnerNumberModel(Math.max(100L, task.getRetryBackoffMs()), 100L, 86_400_000L, 100L));

        nameField.setText(task.getName());
        categoryField.setText(task.getCategory());
        tagsField.setText(String.join(", ", task.getTags()));
        notesField.setText(task.getNotes());

        setLayout(new BorderLayout(10, 10));
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        setSize(800, 360);
        setLocationRelativeTo(owner);
        updateRecurrenceControlState();
        updateRetryControlState();
        recurrenceIntervalSpinner.addChangeListener(event -> updateRecurrenceControlState());
        retryLimitSpinner.addChangeListener(event -> updateRetryControlState());
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 4, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        panel.add(new JLabel("Task Name"));
        panel.add(new JLabel("Category"));
        panel.add(new JLabel("Tags (comma separated)"));
        panel.add(new JLabel("Notes"));

        panel.add(nameField);
        panel.add(categoryField);
        panel.add(tagsField);
        panel.add(notesField);

        panel.add(new JLabel("Priority (1 = highest)"));
        panel.add(new JLabel("Duration (ms)"));
        panel.add(new JLabel("Deadline from now (ms)"));
        panel.add(new JLabel("Repeat after completion (ms)"));

        panel.add(prioritySpinner);
        panel.add(durationSpinner);
        panel.add(deadlineOffsetSpinner);
        panel.add(recurrenceIntervalSpinner);

        panel.add(new JLabel("Occurrences"));
        panel.add(new JLabel("Auto retries"));
        panel.add(new JLabel("Retry backoff (ms)"));
        panel.add(new JLabel(" "));

        panel.add(occurrencesSpinner);
        panel.add(retryLimitSpinner);
        panel.add(retryBackoffSpinner);
        panel.add(new JLabel(" "));

        panel.add(new JLabel("You can edit queued or paused tasks."));
        panel.add(new JLabel("Retries already used become the minimum allowed."));
        panel.add(new JLabel("Repeat delay 0 disables recurrence."));
        panel.add(new JLabel("Retry backoff is disabled when retries = 0."));
        return panel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dispose());

        JButton saveButton = new JButton("Save Changes");
        saveButton.addActionListener(event -> handleSave());

        panel.add(cancelButton);
        panel.add(saveButton);
        return panel;
    }

    private void handleSave() {
        try {
            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            String notes = notesField.getText().trim();
            int priority = (int) prioritySpinner.getValue();
            long durationMs = ((Number) durationSpinner.getValue()).longValue();
            long deadlineOffsetMs = ((Number) deadlineOffsetSpinner.getValue()).longValue();
            long recurrenceIntervalMs = ((Number) recurrenceIntervalSpinner.getValue()).longValue();
            int occurrences = ((Number) occurrencesSpinner.getValue()).intValue();
            int retryLimit = ((Number) retryLimitSpinner.getValue()).intValue();
            long retryBackoffMs = ((Number) retryBackoffSpinner.getValue()).longValue();

            if (recurrenceIntervalMs <= 0L) {
                occurrences = originalTask.getOccurrenceIndex();
            }
            if (retryLimit <= 0) {
                retryBackoffMs = 0L;
            }

            result = new TaskConfiguration(
                    name,
                    priority,
                    durationMs,
                    System.currentTimeMillis() + deadlineOffsetMs,
                    category,
                    TaskConfiguration.parseTags(tagsField.getText()),
                    notes,
                    recurrenceIntervalMs,
                    occurrences,
                    retryLimit,
                    retryBackoffMs
            );
            dispose();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Invalid Task Configuration",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateRecurrenceControlState() {
        long interval = ((Number) recurrenceIntervalSpinner.getValue()).longValue();
        boolean recurring = interval > 0L;
        occurrencesSpinner.setEnabled(recurring);
        if (!recurring) {
            occurrencesSpinner.setValue(Math.max(1, originalTask.getOccurrenceIndex()));
        }
    }

    private void updateRetryControlState() {
        int retryLimit = ((Number) retryLimitSpinner.getValue()).intValue();
        boolean enabled = retryLimit > 0;
        retryBackoffSpinner.setEnabled(enabled);
        if (!enabled) {
            retryBackoffSpinner.setValue(2000L);
        }
    }
}
