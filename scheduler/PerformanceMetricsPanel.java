package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridLayout;

public class PerformanceMetricsPanel extends JPanel {
    private final JLabel totalTasksLabel = new JLabel("Total Tasks: 0");
    private final JLabel activeTasksLabel = new JLabel("Active Workspace: 0");
    private final JLabel historyTasksLabel = new JLabel("History Entries: 0");
    private final JLabel queuedTasksLabel = new JLabel("Ready Queue: 0");
    private final JLabel deferredTasksLabel = new JLabel("Deferred Queue: 0");
    private final JLabel runningTasksLabel = new JLabel("Running: 0");
    private final JLabel pausedTasksLabel = new JLabel("Paused: 0");
    private final JLabel completedTasksLabel = new JLabel("Completed: 0");
    private final JLabel missedTasksLabel = new JLabel("Missed: 0");
    private final JLabel canceledTasksLabel = new JLabel("Canceled: 0");
    private final JLabel atRiskTasksLabel = new JLabel("At Risk: 0");
    private final JLabel retryTasksLabel = new JLabel("Retry Attempts: 0");
    private final JLabel avgExecTimeLabel = new JLabel("Avg Executed Time: 0 ms");
    private final JLabel avgWaitTimeLabel = new JLabel("Avg Wait Time: 0 ms");
    private final JLabel onTimeRateLabel = new JLabel("On-Time Rate: 100%");
    private final JLabel preemptionsLabel = new JLabel("Total Preemptions: 0");

    public PerformanceMetricsPanel() {
        setLayout(new GridLayout(16, 1, 4, 4));
        setBorder(BorderFactory.createTitledBorder("Local Scheduler Health"));
        add(totalTasksLabel);
        add(activeTasksLabel);
        add(historyTasksLabel);
        add(queuedTasksLabel);
        add(deferredTasksLabel);
        add(runningTasksLabel);
        add(pausedTasksLabel);
        add(completedTasksLabel);
        add(missedTasksLabel);
        add(canceledTasksLabel);
        add(atRiskTasksLabel);
        add(retryTasksLabel);
        add(avgExecTimeLabel);
        add(avgWaitTimeLabel);
        add(onTimeRateLabel);
        add(preemptionsLabel);
    }

    public void updateMetrics(SchedulerMetrics metrics) {
        totalTasksLabel.setText("Total Tasks: " + metrics.totalTasks());
        activeTasksLabel.setText("Active Workspace: " + metrics.activeTasks());
        historyTasksLabel.setText("History Entries: " + metrics.historyTasks());
        queuedTasksLabel.setText("Ready Queue: " + metrics.queuedTasks());
        deferredTasksLabel.setText("Deferred Queue: " + metrics.deferredTasks());
        runningTasksLabel.setText("Running: " + metrics.runningTasks());
        pausedTasksLabel.setText("Paused: " + metrics.pausedTasks());
        completedTasksLabel.setText("Completed: " + metrics.completedTasks());
        missedTasksLabel.setText("Missed: " + metrics.missedTasks());
        canceledTasksLabel.setText("Canceled: " + metrics.canceledTasks());
        atRiskTasksLabel.setText("At Risk: " + metrics.atRiskTasks());
        retryTasksLabel.setText("Retry Attempts: " + metrics.automaticRetryTasks());
        avgExecTimeLabel.setText("Avg Executed Time: " + metrics.averageExecutedDurationMs() + " ms");
        avgWaitTimeLabel.setText("Avg Wait Time: " + metrics.averageWaitTimeMs() + " ms");
        onTimeRateLabel.setText("On-Time Rate: " + metrics.onTimeCompletionRate() + "%");
        preemptionsLabel.setText("Total Preemptions: " + metrics.totalPreemptions());
    }
}
