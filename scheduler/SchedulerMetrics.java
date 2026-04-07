package scheduler;

public record SchedulerMetrics(
        int totalTasks,
        int activeTasks,
        int historyTasks,
        int queuedTasks,
        int deferredTasks,
        int runningTasks,
        int pausedTasks,
        int completedTasks,
        int missedTasks,
        int canceledTasks,
        int atRiskTasks,
        int automaticRetryTasks,
        long averageExecutedDurationMs,
        long averageWaitTimeMs,
        int onTimeCompletionRate,
        int totalPreemptions
) {
}
