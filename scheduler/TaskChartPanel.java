package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TaskChartPanel extends JPanel {
    private List<ScheduledTask> tasks = new ArrayList<>();

    public TaskChartPanel() {
        setPreferredSize(new Dimension(820, 280));
        setBorder(BorderFactory.createTitledBorder("Execution Timeline"));
    }

    public void setTasks(List<ScheduledTask> tasks) {
        this.tasks = new ArrayList<>(tasks);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        if (tasks.isEmpty()) {
            graphics.drawString("No task execution history yet.", 16, 24);
            return;
        }

        List<ScheduledTask> startedTasks = tasks.stream()
                .filter(task -> !task.getExecutionSegments().isEmpty())
                .sorted(Comparator.comparingLong(ScheduledTask::getCreatedAt))
                .toList();

        if (startedTasks.isEmpty()) {
            graphics.drawString("Tasks are queued but none have started yet.", 16, 24);
            return;
        }

        long minTime = startedTasks.stream()
                .flatMap(task -> task.getExecutionSegments().stream())
                .mapToLong(ScheduledTask.ExecutionSegment::getStartedAt)
                .min()
                .orElse(System.currentTimeMillis());

        long maxTime = startedTasks.stream()
                .flatMap(task -> task.getExecutionSegments().stream())
                .mapToLong(ScheduledTask.ExecutionSegment::getEndedAt)
                .max()
                .orElse(minTime + 1L);

        long range = Math.max(1L, maxTime - minTime);
        int leftPadding = 16;
        int labelWidth = 160;
        int chartWidth = Math.max(1, getWidth() - leftPadding - labelWidth - 24);
        int y = 28;

        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString("Each bar shows actual execution slices after pauses/preemption.", leftPadding, 16);

        for (ScheduledTask task : startedTasks) {
            graphics.setColor(Color.BLACK);
            graphics.drawString(task.getName() + " [" + task.getStatus().name() + "]", leftPadding, y + 14);

            for (ScheduledTask.ExecutionSegment segment : task.getExecutionSegments()) {
                int x = leftPadding + labelWidth + (int) (((segment.getStartedAt() - minTime) * chartWidth) / (double) range);
                int width = Math.max(2, (int) (((segment.getEndedAt() - segment.getStartedAt()) * chartWidth) / (double) range));
                graphics.setColor(colorForStatus(task.getStatus()));
                graphics.fillRect(x, y, width, 16);
                graphics.setColor(Color.BLACK);
                graphics.drawRect(x, y, width, 16);
            }

            y += 26;
            if (y > getHeight() - 24) {
                graphics.drawString("... more tasks omitted", leftPadding, getHeight() - 10);
                break;
            }
        }
    }

    private Color colorForStatus(TaskStatus status) {
        return switch (status) {
            case RUNNING -> new Color(255, 193, 7);
            case SCHEDULED -> new Color(149, 117, 205);
            case QUEUED -> new Color(173, 216, 230);
            case PAUSED -> new Color(255, 235, 59);
            case COMPLETED -> new Color(76, 175, 80);
            case MISSED -> new Color(244, 67, 54);
            case CANCELED -> new Color(158, 158, 158);
        };
    }
}
