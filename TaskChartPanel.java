package scheduler;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TaskChartPanel extends JPanel {
    private final List<ScheduledTask> completed;
    private final List<ScheduledTask> missed;
    private final List<ScheduledTask> interrupted;

    public TaskChartPanel(List<ScheduledTask> completed, List<ScheduledTask> interrupted, List<ScheduledTask> missed) {
        this.completed = completed;
        this.missed = missed;
        this.interrupted = interrupted;
        setPreferredSize(new Dimension(800, 300));
        setBorder(BorderFactory.createTitledBorder("Task Gantt Chart"));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        List<ScheduledTask> allTasks = new java.util.ArrayList<>();
        allTasks.addAll(completed);
        allTasks.addAll(missed);
        allTasks.addAll(interrupted);

        // Legend
        g.setColor(Color.BLACK);
        g.drawString("Legend: Blue = Completed, Red = Missed, Orange = Interrupted", 10, 25);

        if (allTasks.isEmpty()) return;

        long minTime = allTasks.stream().mapToLong(ScheduledTask::getStartTimestamp).min().orElse(System.currentTimeMillis());
        long maxTime = allTasks.stream().mapToLong(ScheduledTask::getEndTimestamp).max().orElse(System.currentTimeMillis());

        long timeRange = maxTime - minTime + 1;
        double timeScale = getWidth() / (double) timeRange;

        int y = 45;
        for (ScheduledTask task : allTasks) {
            long start = task.getStartTimestamp();
            long end = task.getEndTimestamp();

            int x = (int) ((start - minTime) * timeScale);
            int width = Math.max(1, (int) ((end - start) * timeScale));

            Color color = Color.YELLOW;
            if (missed.contains(task)) color = Color.RED;
            else if (interrupted.contains(task)) color = Color.ORANGE;

            g.setColor(color);
            g.fillRect(x, y, width, 20);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, width, 20);
            g.drawString(task.getName(), x + 2, y + 15);

            y += 30;

            
        }
    }

    private void drawTasks(Graphics g, List<ScheduledTask> tasks, long baseTime, int y, Color color) {
        for (ScheduledTask task : tasks) {
            long start = task.getStartTimestamp();
            long end = task.getEndTimestamp();
            if (start == 0 || end == 0) continue; // skip if not recorded

            int x = (int) ((start - baseTime) / 10);
            int width = (int) ((end - start) / 10);
            g.setColor(color);
            g.fillRect(x, y, Math.max(width, 5), 20);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, Math.max(width, 5), 20);
            g.drawString(task.getName(), x, y - 5);
        }
    }
}
