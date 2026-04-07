package scheduler;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ScheduledTask implements Comparable<ScheduledTask>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final long createdAt;

    private String name;
    private int priority; // 1 = highest priority, 10 = lowest
    private long totalDurationMs;
    private long remainingDurationMs;
    private long deadlineEpochMs;
    private TaskStatus status;
    private long updatedAt;
    private long firstStartedAt;
    private long finishedAt;
    private int preemptionCount;
    private final List<ExecutionSegment> executionSegments;

    private String category;
    private List<String> tags;
    private String notes;
    private long readyAtEpochMs;
    private long deadlineOffsetMs;
    private long recurrenceIntervalMs;
    private int recurrenceLimit;
    private int occurrenceIndex;
    private String seriesId;

    private int retryLimit;
    private long retryBackoffMs;
    private int retryAttempt;
    private String retryParentTaskId;

    public ScheduledTask(TaskConfiguration configuration) {
        this(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                1,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                configuration,
                0,
                ""
        );
    }

    private ScheduledTask(
            String id,
            String seriesId,
            int occurrenceIndex,
            long createdAt,
            long readyAtEpochMs,
            TaskConfiguration configuration,
            int retryAttempt,
            String retryParentTaskId
    ) {
        long safeReadyAt = Math.max(createdAt, readyAtEpochMs);
        long safeDeadline = Math.max(safeReadyAt + 1L, configuration.deadlineEpochMs());

        this.id = id;
        this.createdAt = createdAt;
        this.name = configuration.name().trim();
        this.priority = configuration.priority();
        this.totalDurationMs = configuration.durationMs();
        this.remainingDurationMs = configuration.durationMs();
        this.deadlineEpochMs = safeDeadline;
        this.status = TaskStatus.QUEUED;
        this.updatedAt = createdAt;
        this.executionSegments = new ArrayList<>();

        this.category = TaskConfiguration.normalizeOptional(configuration.category());
        this.tags = new ArrayList<>(configuration.tags());
        this.notes = TaskConfiguration.normalizeOptional(configuration.notes());
        this.readyAtEpochMs = safeReadyAt;
        this.deadlineOffsetMs = Math.max(1L, safeDeadline - safeReadyAt);
        this.recurrenceIntervalMs = Math.max(0L, configuration.recurrenceIntervalMs());
        this.recurrenceLimit = Math.max(1, configuration.recurrenceLimit());
        if (this.recurrenceIntervalMs == 0L) {
            this.recurrenceLimit = 1;
        }
        this.occurrenceIndex = Math.max(1, occurrenceIndex);
        this.seriesId = seriesId;

        this.retryLimit = Math.max(0, configuration.retryLimit());
        this.retryBackoffMs = this.retryLimit == 0 ? 0L : Math.max(100L, configuration.retryBackoffMs());
        this.retryAttempt = Math.max(0, retryAttempt);
        this.retryParentTaskId = retryParentTaskId == null ? "" : retryParentTaskId;
    }

    private ScheduledTask(ScheduledTask source) {
        this.id = source.id;
        this.createdAt = source.createdAt;
        this.name = source.name;
        this.priority = source.priority;
        this.totalDurationMs = source.totalDurationMs;
        this.remainingDurationMs = source.remainingDurationMs;
        this.deadlineEpochMs = source.deadlineEpochMs;
        this.status = source.status;
        this.updatedAt = source.updatedAt;
        this.firstStartedAt = source.firstStartedAt;
        this.finishedAt = source.finishedAt;
        this.preemptionCount = source.preemptionCount;
        this.executionSegments = new ArrayList<>();
        for (ExecutionSegment segment : source.executionSegments) {
            this.executionSegments.add(segment.copy());
        }

        this.category = source.category;
        this.tags = source.tags == null ? new ArrayList<>() : new ArrayList<>(source.tags);
        this.notes = source.notes;
        this.readyAtEpochMs = source.readyAtEpochMs;
        this.deadlineOffsetMs = source.deadlineOffsetMs;
        this.recurrenceIntervalMs = source.recurrenceIntervalMs;
        this.recurrenceLimit = source.recurrenceLimit;
        this.occurrenceIndex = source.occurrenceIndex;
        this.seriesId = source.seriesId;

        this.retryLimit = source.retryLimit;
        this.retryBackoffMs = source.retryBackoffMs;
        this.retryAttempt = source.retryAttempt;
        this.retryParentTaskId = source.retryParentTaskId;
    }

    public synchronized ScheduledTask copy() {
        normalizeMutableFields();
        return new ScheduledTask(this);
    }

    public synchronized void prepareForRestore(long now) {
        normalizeMutableFields();
        closeOpenSegment(now);

        if (status.isTerminal()) {
            return;
        }

        if (remainingDurationMs <= 0L) {
            markCompleted(now);
            return;
        }

        if (now > deadlineEpochMs) {
            markMissed(now);
            return;
        }

        if (status == TaskStatus.RUNNING) {
            status = TaskStatus.QUEUED;
        } else if (status != TaskStatus.PAUSED) {
            status = TaskStatus.QUEUED;
        }

        updatedAt = now;
    }

    public synchronized void markQueued(long now) {
        normalizeMutableFields();
        closeOpenSegment(now);
        if (!status.isTerminal()) {
            status = TaskStatus.QUEUED;
            updatedAt = now;
        }
    }

    public synchronized void markRunning(long now) {
        normalizeMutableFields();
        if (status.isTerminal()) {
            return;
        }
        if (firstStartedAt == 0L) {
            firstStartedAt = now;
        }
        status = TaskStatus.RUNNING;
        updatedAt = now;
        executionSegments.add(new ExecutionSegment(now));
    }

    public synchronized void markPaused(long now) {
        normalizeMutableFields();
        closeOpenSegment(now);
        if (!status.isTerminal()) {
            status = TaskStatus.PAUSED;
            updatedAt = now;
        }
    }

    public synchronized void markCanceled(long now) {
        normalizeMutableFields();
        closeOpenSegment(now);
        status = TaskStatus.CANCELED;
        updatedAt = now;
        finishedAt = now;
    }

    public synchronized void markCompleted(long now) {
        normalizeMutableFields();
        remainingDurationMs = 0L;
        closeOpenSegment(now);
        status = TaskStatus.COMPLETED;
        updatedAt = now;
        finishedAt = now;
    }

    public synchronized void markMissed(long now) {
        normalizeMutableFields();
        closeOpenSegment(now);
        status = TaskStatus.MISSED;
        updatedAt = now;
        finishedAt = now;
    }

    public synchronized void recordPreemption(long now) {
        normalizeMutableFields();
        preemptionCount++;
        closeOpenSegment(now);
        if (!status.isTerminal()) {
            status = TaskStatus.QUEUED;
            updatedAt = now;
        }
    }

    public synchronized void consumeTimeSlice(long elapsedMs, long now) {
        normalizeMutableFields();
        if (status != TaskStatus.RUNNING) {
            return;
        }

        remainingDurationMs = Math.max(0L, remainingDurationMs - elapsedMs);
        if (!executionSegments.isEmpty()) {
            executionSegments.get(executionSegments.size() - 1).setEndedAt(now);
        }
        updatedAt = now;
    }

    public synchronized void updateConfiguration(TaskConfiguration configuration, long now) {
        normalizeMutableFields();

        long executedDuration = getExecutedDurationMs();
        if (configuration.durationMs() < executedDuration) {
            throw new IllegalArgumentException(
                    "Duration cannot be smaller than already executed time (" + executedDuration + " ms)."
            );
        }

        this.name = configuration.name().trim();
        this.priority = configuration.priority();
        this.totalDurationMs = configuration.durationMs();
        this.remainingDurationMs = configuration.durationMs() - executedDuration;
        this.deadlineEpochMs = configuration.deadlineEpochMs();
        this.category = TaskConfiguration.normalizeOptional(configuration.category());
        this.tags = new ArrayList<>(configuration.tags());
        this.notes = TaskConfiguration.normalizeOptional(configuration.notes());
        this.deadlineOffsetMs = Math.max(1L, deadlineEpochMs - readyAtEpochMs);
        this.recurrenceIntervalMs = Math.max(0L, configuration.recurrenceIntervalMs());
        this.recurrenceLimit = Math.max(occurrenceIndex, configuration.recurrenceLimit());
        if (recurrenceIntervalMs == 0L) {
            recurrenceLimit = occurrenceIndex;
        }
        this.retryLimit = Math.max(retryAttempt, configuration.retryLimit());
        this.retryBackoffMs = this.retryLimit == 0 ? 0L : Math.max(100L, configuration.retryBackoffMs());
        this.updatedAt = now;
    }

    public synchronized ScheduledTask createManualCloneTask(long now) {
        normalizeMutableFields();
        TaskConfiguration configuration = new TaskConfiguration(
                name,
                priority,
                totalDurationMs,
                now + deadlineOffsetMs,
                category,
                getTags(),
                notes,
                recurrenceIntervalMs,
                recurrenceLimit,
                retryLimit,
                retryBackoffMs
        );
        return new ScheduledTask(configuration);
    }

    public synchronized ScheduledTask createAutomaticRetryTask(long now) {
        normalizeMutableFields();
        if (!hasRetryRemaining()) {
            return null;
        }

        long nextReadyAt = now + retryBackoffMs;
        TaskConfiguration configuration = new TaskConfiguration(
                name,
                priority,
                totalDurationMs,
                nextReadyAt + deadlineOffsetMs,
                category,
                getTags(),
                notes,
                recurrenceIntervalMs,
                recurrenceLimit,
                retryLimit,
                retryBackoffMs
        );

        return new ScheduledTask(
                UUID.randomUUID().toString(),
                seriesId,
                occurrenceIndex,
                now,
                nextReadyAt,
                configuration,
                retryAttempt + 1,
                id
        );
    }

    public synchronized ScheduledTask createNextOccurrence(long now) {
        normalizeMutableFields();
        if (!hasFutureOccurrence()) {
            return null;
        }

        long nextReadyAt = now + recurrenceIntervalMs;
        TaskConfiguration configuration = new TaskConfiguration(
                name,
                priority,
                totalDurationMs,
                nextReadyAt + deadlineOffsetMs,
                category,
                getTags(),
                notes,
                recurrenceIntervalMs,
                recurrenceLimit,
                retryLimit,
                retryBackoffMs
        );

        return new ScheduledTask(
                UUID.randomUUID().toString(),
                seriesId,
                occurrenceIndex + 1,
                now,
                nextReadyAt,
                configuration,
                0,
                ""
        );
    }

    public synchronized boolean hasFutureOccurrence() {
        normalizeMutableFields();
        return recurrenceIntervalMs > 0L && occurrenceIndex < recurrenceLimit;
    }

    public synchronized boolean hasRetryRemaining() {
        normalizeMutableFields();
        return retryLimit > retryAttempt;
    }

    public synchronized boolean isReady(long now) {
        normalizeMutableFields();
        return readyAtEpochMs <= now;
    }

    public synchronized boolean isDeferred(long now) {
        normalizeMutableFields();
        return status == TaskStatus.QUEUED && readyAtEpochMs > now;
    }

    public synchronized boolean isTerminal() {
        return status.isTerminal();
    }

    public synchronized long getExecutedDurationMs() {
        return Math.max(0L, totalDurationMs - remainingDurationMs);
    }

    public synchronized long getFirstStartWaitMs() {
        if (firstStartedAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, firstStartedAt - readyAtEpochMs);
    }

    public synchronized long getQueueWaitMs(long now) {
        normalizeMutableFields();
        long queueEntry = Math.max(readyAtEpochMs, updatedAt);
        if (status == TaskStatus.QUEUED) {
            return Math.max(0L, now - queueEntry);
        }
        return getFirstStartWaitMs();
    }

    public synchronized long getSlackMs(long now) {
        normalizeMutableFields();
        return deadlineEpochMs - now - remainingDurationMs;
    }

    public synchronized boolean isAtRisk(SchedulerPolicySettings settings, long now) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;
        return !status.isTerminal() && getSlackMs(now) <= safeSettings.deadlineUrgencyWindowMs();
    }

    public synchronized int getEffectivePriority(SchedulerPolicySettings settings, long now) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;
        int effective = priority;

        if (safeSettings.agingEnabled() && status == TaskStatus.QUEUED && readyAtEpochMs <= now) {
            long waitMs = getQueueWaitMs(now);
            int ageBoost = (int) (waitMs / safeSettings.agingQuantumMs());
            effective = Math.max(1, effective - ageBoost);
        }

        if (safeSettings.schedulingMode() == SchedulingMode.ADAPTIVE && isAtRisk(safeSettings, now)) {
            long slack = getSlackMs(now);
            if (slack <= 0L) {
                effective = 1;
            } else {
                int urgencyBoost = Math.max(1, (int) Math.ceil((safeSettings.deadlineUrgencyWindowMs() - slack) / (double) safeSettings.deadlineUrgencyWindowMs() * 2.0));
                effective = Math.max(1, effective - urgencyBoost);
            }
        }

        return effective;
    }

    public synchronized int getProgressPercent() {
        if (totalDurationMs <= 0L) {
            return 0;
        }
        long executed = getExecutedDurationMs();
        return (int) Math.min(100L, Math.round((executed * 100.0) / totalDurationMs));
    }

    public synchronized String getRecurrenceSummary() {
        normalizeMutableFields();
        if (recurrenceIntervalMs <= 0L || recurrenceLimit <= 1) {
            return "Once";
        }
        return "Every " + recurrenceIntervalMs + " ms • " + occurrenceIndex + "/" + recurrenceLimit;
    }

    public synchronized String getRetrySummary() {
        normalizeMutableFields();
        if (retryLimit <= 0) {
            return "Off";
        }
        return retryAttempt + "/" + retryLimit + " @ " + retryBackoffMs + " ms";
    }

    public synchronized TaskConfiguration toConfiguration() {
        normalizeMutableFields();
        return new TaskConfiguration(
                name,
                priority,
                totalDurationMs,
                deadlineEpochMs,
                category,
                getTags(),
                notes,
                recurrenceIntervalMs,
                recurrenceLimit,
                retryLimit,
                retryBackoffMs
        );
    }

    private void closeOpenSegment(long now) {
        if (!executionSegments.isEmpty()) {
            executionSegments.get(executionSegments.size() - 1).setEndedAt(now);
        }
    }

    private void normalizeMutableFields() {
        if (category == null) {
            category = "";
        }
        if (notes == null) {
            notes = "";
        }
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (readyAtEpochMs == 0L) {
            readyAtEpochMs = createdAt;
        }
        if (deadlineOffsetMs <= 0L) {
            deadlineOffsetMs = Math.max(1L, deadlineEpochMs - readyAtEpochMs);
        }
        if (recurrenceLimit <= 0) {
            recurrenceLimit = 1;
        }
        if (recurrenceIntervalMs <= 0L) {
            recurrenceIntervalMs = 0L;
            recurrenceLimit = Math.max(1, occurrenceIndex);
        }
        if (occurrenceIndex <= 0) {
            occurrenceIndex = 1;
        }
        if (seriesId == null || seriesId.isBlank()) {
            seriesId = id;
        }
        if (retryLimit < 0) {
            retryLimit = 0;
        }
        if (retryLimit == 0) {
            retryBackoffMs = 0L;
        } else if (retryBackoffMs <= 0L) {
            retryBackoffMs = 1_000L;
        }
        if (retryAttempt < 0) {
            retryAttempt = 0;
        }
        if (retryParentTaskId == null) {
            retryParentTaskId = "";
        }
    }

    public synchronized String getId() {
        return id;
    }

    public synchronized long getCreatedAt() {
        return createdAt;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized int getPriority() {
        return priority;
    }

    public synchronized long getTotalDurationMs() {
        return totalDurationMs;
    }

    public synchronized long getRemainingDurationMs() {
        return remainingDurationMs;
    }

    public synchronized long getDeadlineEpochMs() {
        return deadlineEpochMs;
    }

    public synchronized TaskStatus getStatus() {
        return status;
    }

    public synchronized long getUpdatedAt() {
        return updatedAt;
    }

    public synchronized long getFirstStartedAt() {
        return firstStartedAt;
    }

    public synchronized long getFinishedAt() {
        return finishedAt;
    }

    public synchronized int getPreemptionCount() {
        return preemptionCount;
    }

    public synchronized List<ExecutionSegment> getExecutionSegments() {
        List<ExecutionSegment> copies = new ArrayList<>();
        for (ExecutionSegment segment : executionSegments) {
            copies.add(segment.copy());
        }
        return copies;
    }

    public synchronized String getCategory() {
        normalizeMutableFields();
        return category;
    }

    public synchronized List<String> getTags() {
        normalizeMutableFields();
        return new ArrayList<>(tags);
    }

    public synchronized String getTagsDisplay() {
        normalizeMutableFields();
        return tags.isEmpty() ? "-" : String.join(", ", tags);
    }

    public synchronized String getNotes() {
        normalizeMutableFields();
        return notes;
    }

    public synchronized long getReadyAtEpochMs() {
        normalizeMutableFields();
        return readyAtEpochMs;
    }

    public synchronized long getDeadlineOffsetMs() {
        normalizeMutableFields();
        return deadlineOffsetMs;
    }

    public synchronized long getRecurrenceIntervalMs() {
        normalizeMutableFields();
        return recurrenceIntervalMs;
    }

    public synchronized int getRecurrenceLimit() {
        normalizeMutableFields();
        return recurrenceLimit;
    }

    public synchronized int getOccurrenceIndex() {
        normalizeMutableFields();
        return occurrenceIndex;
    }

    public synchronized String getSeriesId() {
        normalizeMutableFields();
        return seriesId;
    }

    public synchronized int getRetryLimit() {
        normalizeMutableFields();
        return retryLimit;
    }

    public synchronized long getRetryBackoffMs() {
        normalizeMutableFields();
        return retryBackoffMs;
    }

    public synchronized int getRetryAttempt() {
        normalizeMutableFields();
        return retryAttempt;
    }

    public synchronized String getRetryParentTaskId() {
        normalizeMutableFields();
        return retryParentTaskId;
    }

    @Override
    public synchronized int compareTo(ScheduledTask other) {
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        int deadlineCompare = Long.compare(this.deadlineEpochMs, other.deadlineEpochMs);
        if (deadlineCompare != 0) {
            return deadlineCompare;
        }

        return Long.compare(this.createdAt, other.createdAt);
    }

    @Override
    public synchronized boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScheduledTask task)) {
            return false;
        }
        return Objects.equals(id, task.id);
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hash(id);
    }

    public static class ExecutionSegment implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final long startedAt;
        private long endedAt;

        public ExecutionSegment(long startedAt) {
            this.startedAt = startedAt;
            this.endedAt = startedAt;
        }

        private ExecutionSegment(ExecutionSegment source) {
            this.startedAt = source.startedAt;
            this.endedAt = source.endedAt;
        }

        public ExecutionSegment copy() {
            return new ExecutionSegment(this);
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getEndedAt() {
            return endedAt;
        }

        public void setEndedAt(long endedAt) {
            this.endedAt = Math.max(this.startedAt, endedAt);
        }
    }
}
