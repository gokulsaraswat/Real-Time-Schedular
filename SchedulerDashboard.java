package scheduler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class SchedulerDashboard extends JFrame {
    private final JTextArea logArea;
    private final DefaultListModel<String> completedListModel;
    private final DefaultListModel<String> interruptedListModel;
    private final DefaultListModel<String> missedListModel;
    private final PerformanceMetricsPanel metricsPanel;
    private final TaskProgressPanel progressPanel;
    private final TaskChartPanel chartPanel;

    public SchedulerDashboard(
        BlockingQueue<String> logQueue,
        List<ScheduledTask> completedTasks,
        List<ScheduledTask> interruptedTasks,
        List<ScheduledTask> missedDeadlines
    ) {
        this.progressPanel = new TaskProgressPanel();
        this.metricsPanel = new PerformanceMetricsPanel();
        this.chartPanel = new TaskChartPanel(completedTasks, interruptedTasks, missedDeadlines);

        setTitle("Real-Time Scheduler Dashboard");
        setSize(1100, 750);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ==================== TOP: Progress ====================
        add(progressPanel, BorderLayout.NORTH);

        // ==================== CENTER: Logs, Metrics, Chart, Status ====================
        JPanel centerPanel = new JPanel(new BorderLayout());

        // ----- Left side: Metrics + Logs -----
        JPanel leftPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Execution Logs"));

        leftPanel.add(metricsPanel, BorderLayout.NORTH);
        leftPanel.add(logScroll, BorderLayout.CENTER);

        // ----- Right side: Chart + Status -----
        JPanel rightPanel = new JPanel(new BorderLayout());
        chartPanel.setBorder(BorderFactory.createTitledBorder("Task Gantt Chart"));
        rightPanel.add(chartPanel, BorderLayout.NORTH);

        // Task Status Lists
        completedListModel = new DefaultListModel<>();
        interruptedListModel = new DefaultListModel<>();
        missedListModel = new DefaultListModel<>();

        JList<String> completedList = new JList<>(completedListModel);
        JList<String> interruptedList = new JList<>(interruptedListModel);
        JList<String> missedList = new JList<>(missedListModel);

        completedList.setBorder(BorderFactory.createTitledBorder("Completed"));
        interruptedList.setBorder(BorderFactory.createTitledBorder("Interrupted"));
        missedList.setBorder(BorderFactory.createTitledBorder("Missed Deadlines"));

        JPanel statusPanel = new JPanel(new GridLayout(1, 3));
        statusPanel.add(new JScrollPane(completedList));
        statusPanel.add(new JScrollPane(interruptedList));
        statusPanel.add(new JScrollPane(missedList));

        rightPanel.add(statusPanel, BorderLayout.CENTER);

        // Add both sides to center
        centerPanel.add(leftPanel, BorderLayout.CENTER);
        centerPanel.add(rightPanel, BorderLayout.EAST);

        add(centerPanel, BorderLayout.CENTER);

        // ==================== SOUTH: Add Task Panel ====================
        JPanel addPanel = new JPanel(new GridLayout(2, 5));
        JTextField nameField = new JTextField("Task-" + (new Random().nextInt(100)));
        JTextField priorityField = new JTextField("5");
        JTextField durationField = new JTextField("3000");
        JTextField deadlineField = new JTextField(Long.toString(System.currentTimeMillis() + 5000));
        JButton addButton = new JButton("Add Task");

        addPanel.add(new JLabel("Name"));
        addPanel.add(new JLabel("Priority"));
        addPanel.add(new JLabel("Duration (ms)"));
        addPanel.add(new JLabel("Deadline (ms since epoch)"));
        addPanel.add(new JLabel(""));

        addPanel.add(nameField);
        addPanel.add(priorityField);
        addPanel.add(durationField);
        addPanel.add(deadlineField);
        addPanel.add(addButton);

        // Buttons for log actions
        JButton exportLogsButton = new JButton("Export Logs");
        JButton viewLogsButton = new JButton("View Logs");

        exportLogsButton.addActionListener(e -> {
            LoggerUtil.exportToFile("scheduler_logs.txt");
            JOptionPane.showMessageDialog(this, "Logs exported to scheduler_logs.txt");
        });

        viewLogsButton.addActionListener(e -> {
            JTextArea area = new JTextArea(String.join("\n", LoggerUtil.getLogs()));
            area.setEditable(false);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(this, scroll, "Log Viewer", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportLogsButton);
        buttonPanel.add(viewLogsButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(addPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);

        // ==================== Action Listeners ====================
        addButton.addActionListener((ActionEvent e) -> {
            try {
                String name = nameField.getText().trim();
                int priority = Integer.parseInt(priorityField.getText().trim());
                long duration = Long.parseLong(durationField.getText().trim());
                long deadline = Long.parseLong(deadlineField.getText().trim());

                ScheduledTask task = new ScheduledTask(name, priority, duration, deadline);
                RealTimeScheduler.addTask(task);
                logArea.append("[USER ADDED] " + name + " added.\n");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ==================== Timers ====================
        new Timer(2000, e -> {
            chartPanel.repaint();

            int total = completedTasks.size() + interruptedTasks.size() + missedDeadlines.size();
            long avgExecTime = (long) completedTasks.stream()
                .mapToLong(t -> t.getEndTimestamp() - t.getStartTimestamp())
                .average().orElse(0.0);
            metricsPanel.updateMetrics(total, completedTasks.size(), interruptedTasks.size(), missedDeadlines.size(), avgExecTime);
        }).start();

        // Log update thread
        new Thread(() -> {
            while (true) {
                try {
                    String log = logQueue.take();
                    SwingUtilities.invokeLater(() -> logArea.append(log + "\n"));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Status list update
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    SwingUtilities.invokeLater(() -> {
                        updateList(completedListModel, completedTasks);
                        updateList(interruptedListModel, interruptedTasks);
                        updateList(missedListModel, missedDeadlines);
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        setVisible(true);
    }

    private void updateList(DefaultListModel<String> model, List<ScheduledTask> tasks) {
        model.clear();
        synchronized (tasks) {
            for (ScheduledTask task : tasks) {
                model.addElement(task.getName());
            }
        }
    }

    public TaskProgressPanel getProgressPanel() {
        return progressPanel;
    }

    public PerformanceMetricsPanel getMetricsPanel() {
        return metricsPanel;
    }
}
