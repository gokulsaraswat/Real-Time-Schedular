package scheduler;

public enum AlertSeverity {
    INFO("Info"),
    SUCCESS("Success"),
    WARNING("Warning"),
    ERROR("Error");

    private final String label;

    AlertSeverity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
