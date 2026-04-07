package scheduler;

public record SchedulerAlert(
        long createdAt,
        AlertSeverity severity,
        String taskId,
        String taskName,
        String message
) {
    public SchedulerAlert {
        severity = severity == null ? AlertSeverity.INFO : severity;
        taskId = taskId == null ? "" : taskId;
        taskName = taskName == null ? "" : taskName;
        message = message == null ? "" : message;
    }

    public String displayTaskName() {
        return taskName.isBlank() ? "-" : taskName;
    }
}
