package scheduler;

public enum SchedulingMode {
    PRIORITY_FIRST("Priority First"),
    DEADLINE_FIRST("Deadline First"),
    ADAPTIVE("Adaptive");

    private final String displayName;

    SchedulingMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
