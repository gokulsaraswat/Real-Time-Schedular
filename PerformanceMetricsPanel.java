// PerformanceMetricsPanel.java
package scheduler;

import javax.swing.*;
import java.awt.*;

public class PerformanceMetricsPanel extends JPanel {
    private final JLabel totalTasksLabel = new JLabel("Total Tasks: 0");
    private final JLabel completedTasksLabel = new JLabel("Completed: 0");
    private final JLabel interruptedTasksLabel = new JLabel("Interrupted: 0");
    private final JLabel missedTasksLabel = new JLabel("Missed: 0");
    private final JLabel avgExecTimeLabel = new JLabel("Avg Execution Time: 0 ms");

    public PerformanceMetricsPanel() {
        setLayout(new GridLayout(5, 1));
        setBorder(BorderFactory.createTitledBorder("Performance Metrics"));
        add(totalTasksLabel);
        add(completedTasksLabel);
        add(interruptedTasksLabel);
        add(missedTasksLabel);
        add(avgExecTimeLabel);
    }

    public void updateMetrics(int total, int completed, int interrupted, int missed, long avgExecTime) {
        totalTasksLabel.setText("Total Tasks: " + total);
        completedTasksLabel.setText("Completed: " + completed);
        interruptedTasksLabel.setText("Interrupted: " + interrupted);
        missedTasksLabel.setText("Missed: " + missed);
        avgExecTimeLabel.setText("Avg Execution Time: " + avgExecTime + " ms");
    }
}

