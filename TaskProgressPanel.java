package scheduler;

import javax.swing.*;
import java.awt.*;

public class TaskProgressPanel extends JPanel {
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel taskLabel = new JLabel("No Task Running");

    public TaskProgressPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Task Progress"));

        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setStringPainted(true);

        add(taskLabel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
    }

    public void updateProgress(String taskName, int progress) {
        taskLabel.setText("Running: " + taskName);
        progressBar.setValue(progress);
    }

    public void clearProgress() {
        taskLabel.setText("No Task Running");
        progressBar.setValue(0);
    }
}
