package scheduler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TaskInputPanel extends JPanel {
    public interface TaskSubmitListener {
        void onTaskSubmit(String name, int priority, long duration, long deadline);
    }

    private final JTextField nameField = new JTextField(10);
    private final JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(3000, 1000, 10000, 500));
    private final JSpinner deadlineOffsetSpinner = new JSpinner(new SpinnerNumberModel(5000, 1000, 20000, 1000));

    public TaskInputPanel(TaskSubmitListener listener) {
        setLayout(new GridLayout(5, 2, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Add New Task"));

        add(new JLabel("Task Name:"));
        add(nameField);

        add(new JLabel("Priority (1-10):"));
        add(prioritySpinner);

        add(new JLabel("Duration (ms):"));
        add(durationSpinner);

        add(new JLabel("Deadline offset (ms):"));
        add(deadlineOffsetSpinner);

        JButton submitButton = new JButton("Submit Task");
        submitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(TaskInputPanel.this, "Task name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int priority = (int) prioritySpinner.getValue();
                long duration = (long) (int) durationSpinner.getValue();
                long deadlineOffset = (long) (int) deadlineOffsetSpinner.getValue();
                long deadline = System.currentTimeMillis() + deadlineOffset;
                listener.onTaskSubmit(name, priority, duration, deadline);
                nameField.setText("");
            }
        });
        add(new JLabel());
        add(submitButton);
    }
}
