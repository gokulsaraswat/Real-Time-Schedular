package scheduler;

public enum TaskStatus {
    SCHEDULED,
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    MISSED,
    CANCELED;

    public boolean isTerminal() {
        return this == COMPLETED || this == MISSED || this == CANCELED;
    }

    public boolean isActive() {
        return this == SCHEDULED || this == QUEUED || this == RUNNING || this == PAUSED;
    }
}
