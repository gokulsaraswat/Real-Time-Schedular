package scheduler;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.*;
import javax.swing.*;

public class RealTimeScheduler {
    private static final int MAX_TASKS = 1000;
    private static final PriorityBlockingQueue<ScheduledTask> taskQueue = new PriorityBlockingQueue<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    private static volatile ScheduledTask currentTask = null;
    private static final List<ScheduledTask> completedTasks = Collections.synchronizedList(new ArrayList<>());
    private static final List<ScheduledTask> interruptedTasks = Collections.synchronizedList(new ArrayList<>());
    private static final List<ScheduledTask> missedDeadlines = Collections.synchronizedList(new ArrayList<>());

    public static void addTask(ScheduledTask task) {
        taskQueue.offer(task);
        try {
            LoggerUtil.log("[USER ADDED] Task: " + task.getName() + " added.");
            logQueue.put("Starting: " + task.getName());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

    // Declare these outside so they’re accessible throughout
    final SchedulerDashboard[] dashboardHolder = new SchedulerDashboard[1];
    final TaskProgressPanel[] progressPanelHolder = new TaskProgressPanel[1];
    final PerformanceMetricsPanel[] metricsPanelHolder = new PerformanceMetricsPanel[1];

    SwingUtilities.invokeLater(() -> {
        SchedulerDashboard dashboard = new SchedulerDashboard(logQueue, completedTasks, interruptedTasks, missedDeadlines);
        dashboardHolder[0] = dashboard;
        progressPanelHolder[0] = dashboard.getProgressPanel();
        metricsPanelHolder[0] = dashboard.getMetricsPanel();

        // Optional: initial UI setup or logs here
    });

    // Initial dummy tasks
    for (int i = 1; i <= 10; i++) {
        int priority = new Random().nextInt(10) + 1;
        long duration = (new Random().nextInt(3) + 2) * 1000L;
        long deadline = System.currentTimeMillis() + duration + 2000;
        taskQueue.offer(new ScheduledTask("Task-" + i, priority, duration, deadline));
    }

    executor.submit(() -> {
        while (true) {
            try {
                ScheduledTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) continue;

                task.setProgressCallback(progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (progressPanelHolder[0] != null)
                            progressPanelHolder[0].updateProgress(task.getName(), progress);
                    });
                });

                if (currentTask != null && task.getPriority() > currentTask.getPriority()) {
                    LoggerUtil.log("Preempting: " + currentTask.getName() + " for " + task.getName());
                    currentTask.interrupt();
                    interruptedTasks.add(currentTask);
                    taskQueue.offer(currentTask);
                }

                currentTask = task;
                LoggerUtil.log("Starting: " + task.getName());

                long startTime = System.currentTimeMillis();
                Future<?> future = executor.submit(task);
                future.get();
                long endTime = System.currentTimeMillis();

                if (System.currentTimeMillis() > task.getDeadline()) {
                    missedDeadlines.add(task);
                    LoggerUtil.log(task.getName() + " missed deadline!");
                }

                completedTasks.add(task);
                LoggerUtil.log(task.getName() + " completed.");

                currentTask = null;

                SwingUtilities.invokeLater(() -> {
                    long total = completedTasks.size() + missedDeadlines.size() + interruptedTasks.size();
                    long avgExec = (long) completedTasks.stream()
                            .mapToLong(t -> t.getEndTimestamp() - t.getStartTimestamp())
                            .average()
                            .orElse(0.0);
                    if (metricsPanelHolder[0] != null) {
                        metricsPanelHolder[0].updateMetrics(
                                (int) total,
                                completedTasks.size(),
                                interruptedTasks.size(),
                                missedDeadlines.size(),
                                (int) avgExec
                        );
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });

    // This line also needs to be delayed until progressPanel is initialized
        SwingUtilities.invokeLater(() -> {
            if (progressPanelHolder[0] != null)
                progressPanelHolder[0].clearProgress();
        });
    }

}

class ScheduledTask implements Runnable, Comparable<ScheduledTask> {
    private final String name;
    private final int priority;
    private final long duration;
    private final long deadline;
    private volatile boolean interrupted = false;
    private long startTimestamp;
    private long endTimestamp;

    private Consumer<Integer> progressCallback;

    public void setProgressCallback(Consumer<Integer> callback) {
        this.progressCallback = callback;
    }

    
    public ScheduledTask(String name, int priority, long duration, long deadline) {
        this.name = name;
        this.priority = priority;
        this.duration = duration;
        this.deadline = deadline;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public String getName() { return name; }
    public int getPriority() { return priority; }
    public long getDeadline() { return deadline; }

    public void interrupt() {
        interrupted = true;
    }

    @Override
    public void run() {
        startTimestamp = System.currentTimeMillis();
        long start = startTimestamp;

        while (!interrupted && System.currentTimeMillis() - start < duration) {
            try {
                Thread.sleep(200);
                if (progressCallback != null) {
                    long elapsed = System.currentTimeMillis() - start;
                    int progress = (int) ((elapsed * 100) / duration);
                    progressCallback.accept(Math.min(progress, 100));
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        endTimestamp = System.currentTimeMillis();
    }

    @Override
    public int compareTo(ScheduledTask other) {
        return Integer.compare(other.priority, this.priority);
    }
}
