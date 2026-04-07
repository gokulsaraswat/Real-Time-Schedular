package scheduler;

/**
 * Immutable task creation/edit request (Phase 2).
 */
public record TaskRequest(
        String name,
        int priority,
        long durationMs,
        long scheduledStartAtEpochMs,
        long deadlineEpochMs,
        String tags,
        long recurrenceIntervalMs,
        int maxRetryAttempts,
        long retryBackoffMs
) {
    public long deadlineWindowMs() {
        return Math.max(1L, deadlineEpochMs - scheduledStartAtEpochMs);
    }
}
