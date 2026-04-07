package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridLayout;

public class TaskInputPanel extends JPanel {
    public interface TaskSubmitListener {
        void onTaskSubmit(TaskConfiguration configuration);
    }

    private final JTextField nameField = new JTextField();
    private final JTextField categoryField = new JTextField();
    private final JTextField tagsField = new JTextField();
    private final JTextField notesField = new JTextField();

    private final JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(3000, 100, 600_000, 100));
    private final JSpinner deadlineOffsetSpinner = new JSpinner(new SpinnerNumberModel(5000, 500, 86_400_000, 500));
    private final JSpinner recurrenceIntervalSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 86_400_000, 500));
    private final JSpinner occurrencesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));
    private final JSpinner retryLimitSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
    private final JSpinner retryBackoffSpinner = new JSpinner(new SpinnerNumberModel(2000, 100, 86_400_000, 100));

    public TaskInputPanel(TaskSubmitListener listener) {
        setLayout(new GridLayout(6, 4, 8, 8));
        setBorder(BorderFactory.createTitledBorder("Add New Task"));

        add(new JLabel("Task Name"));
        add(new JLabel("Category"));
        add(new JLabel("Tags (comma separated)"));
        add(new JLabel("Notes"));

        add(nameField);
        add(categoryField);
        add(tagsField);
        add(notesField);

        add(new JLabel("Priority (1 = highest)"));
        add(new JLabel("Duration (ms)"));
        add(new JLabel("Deadline offset (ms)"));
        add(new JLabel("Repeat after completion (ms)"));

        add(prioritySpinner);
        add(durationSpinner);
        add(deadlineOffsetSpinner);
        add(recurrenceIntervalSpinner);

        add(new JLabel("Occurrences"));
        add(new JLabel("Auto retries"));
        add(new JLabel("Retry backoff (ms)"));
        add(new JLabel(" "));

        add(occurrencesSpinner);
        add(retryLimitSpinner);
        add(retryBackoffSpinner);

        JButton addTaskButton = new JButton("Add Task");
        addTaskButton.addActionListener(event -> {
            try {
                TaskConfiguration configuration = buildConfigurationFromForm();
                listener.onTaskSubmit(configuration);
                resetForm();
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        exception.getMessage(),
                        "Invalid Task",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        addTaskButton.setToolTipText("Adds the task to the local scheduler.");
        add(addTaskButton);

        add(new JLabel("Repeat delay 0 = one-time task."));
        add(new JLabel("Auto retry is used only for missed tasks."));
        add(new JLabel("Retry backoff is disabled when retries = 0."));
        add(new JLabel(" "));

        recurrenceIntervalSpinner.addChangeListener(event -> updateRecurrenceControlState());
        retryLimitSpinner.addChangeListener(event -> updateRetryControlState());
        updateRecurrenceControlState();
        updateRetryControlState();
    }

    private TaskConfiguration buildConfigurationFromForm() {
        String name = nameField.getText().trim();
        String category = categoryField.getText().trim();
        String tagsText = tagsField.getText().trim();
        String notes = notesField.getText().trim();
        int priority = (int) prioritySpinner.getValue();
        long durationMs = ((Number) durationSpinner.getValue()).longValue();
        long deadlineOffsetMs = ((Number) deadlineOffsetSpinner.getValue()).longValue();
        long recurrenceIntervalMs = ((Number) recurrenceIntervalSpinner.getValue()).longValue();
        int occurrences = ((Number) occurrencesSpinner.getValue()).intValue();
        int retryLimit = ((Number) retryLimitSpinner.getValue()).intValue();
        long retryBackoffMs = ((Number) retryBackoffSpinner.getValue()).longValue();

        if (recurrenceIntervalMs <= 0L) {
            occurrences = 1;
        }
        if (retryLimit <= 0) {
            retryBackoffMs = 0L;
        }

        return new TaskConfiguration(
                name,
                priority,
                durationMs,
                System.currentTimeMillis() + deadlineOffsetMs,
                category,
                TaskConfiguration.parseTags(tagsText),
                notes,
                recurrenceIntervalMs,
                occurrences,
                retryLimit,
                retryBackoffMs
        );
    }

    private void resetForm() {
        nameField.setText("");
        categoryField.setText("");
        tagsField.setText("");
        notesField.setText("");
        prioritySpinner.setValue(5);
        durationSpinner.setValue(3000);
        deadlineOffsetSpinner.setValue(5000);
        recurrenceIntervalSpinner.setValue(0);
        occurrencesSpinner.setValue(1);
        retryLimitSpinner.setValue(0);
        retryBackoffSpinner.setValue(2000);
        updateRecurrenceControlState();
        updateRetryControlState();
    }

    private void updateRecurrenceControlState() {
        long interval = ((Number) recurrenceIntervalSpinner.getValue()).longValue();
        boolean recurring = interval > 0L;
        occurrencesSpinner.setEnabled(recurring);
        if (!recurring) {
            occurrencesSpinner.setValue(1);
        }
    }

    private void updateRetryControlState() {
        int retryLimit = ((Number) retryLimitSpinner.getValue()).intValue();
        boolean retryEnabled = retryLimit > 0;
        retryBackoffSpinner.setEnabled(retryEnabled);
        if (!retryEnabled) {
            retryBackoffSpinner.setValue(2000);
        }
    }
}
