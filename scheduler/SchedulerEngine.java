package scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class SchedulerEngine {
    private static final long TICK_MS = 100L;

    private final int maxTasks;
    private final SchedulerStateStore stateStore;
    private final Object stateLock = new Object();
    private final ConcurrentHashMap<String, ScheduledTask> allTasks = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ScheduledTask> readyQueue = new PriorityBlockingQueue<>();
    private final PriorityQueue<ScheduledTask> deferredQueue =
            new PriorityQueue<>(Comparator
                    .comparingLong(ScheduledTask::getReadyAtEpochMs)
                    .thenComparingInt(ScheduledTask::getPriority)
                    .thenComparingLong(ScheduledTask::getCreatedAt));

    private volatile boolean running;
    private Thread schedulerThread;
    private ScheduledTask currentTask;
    private SchedulerPolicySettings policySettings;

    public SchedulerEngine(SchedulerStateStore stateStore, int maxTasks) {
        this.stateStore = stateStore;
        this.maxTasks = maxTasks;
        this.policySettings = stateStore.loadPolicySettings();
        restoreState();
    }

    public void start() {
        synchronized (stateLock) {
            if (running) {
                return;
            }
            running = true;
            schedulerThread = new Thread(this::runLoop, "scheduler-engine");
            schedulerThread.setDaemon(true);
            schedulerThread.start();
        }
    }

    public void shutdownGracefully() {
        Thread threadToJoin;
        synchronized (stateLock) {
            if (!running && schedulerThread == null) {
                persistState();
                return;
            }
            running = false;
            if (currentTask != null && currentTask.getStatus() == TaskStatus.RUNNING) {
                currentTask.markQueued(System.currentTimeMillis());
                enqueueQueuedTask(currentTask, System.currentTimeMillis());
                currentTask = null;
            }
            persistState();
            threadToJoin = schedulerThread;
            schedulerThread = null;
        }

        if (threadToJoin != null) {
            threadToJoin.interrupt();
            try {
                threadToJoin.join(2000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ScheduledTask addTask(String name, int priority, long durationMs, long deadlineEpochMs) {
        return addTask(new TaskConfiguration(
                name,
                priority,
                durationMs,
                deadlineEpochMs,
                "",
                List.of(),
                "",
                0L,
                1,
                0,
                0L
        ));
    }

    public ScheduledTask addTask(TaskConfiguration configuration) {
        validateTaskConfiguration(configuration, System.currentTimeMillis());
        ScheduledTask task = new ScheduledTask(configuration);

        synchronized (stateLock) {
            ensureCapacityLocked();
            allTasks.put(task.getId(), task);
            enqueueQueuedTask(task, System.currentTimeMillis());
            persistState();
        }

        LoggerUtil.log(
                "Added task '" + task.getName()
                        + "' (priority=" + task.getPriority()
                        + ", duration=" + task.getTotalDurationMs() + " ms"
                        + ", category=" + (task.getCategory().isBlank() ? "-" : task.getCategory())
                        + ", recurrence=" + task.getRecurrenceSummary()
                        + ", retry=" + task.getRetrySummary() + ")."
        );
        wakeUpScheduler();
        return task.copy();
    }

    public boolean updateTask(String taskId, TaskConfiguration updatedConfiguration) {
        synchronized (stateLock) {
            ScheduledTask task = allTasks.get(taskId);
            if (task == null || task.isTerminal() || task.getStatus() == TaskStatus.RUNNING) {
                return false;
            }

            validateTaskConfiguration(updatedConfiguration, Math.max(System.currentTimeMillis(), task.getReadyAtEpochMs()));
            removeFromQueues(task);
            task.updateConfiguration(updatedConfiguration, System.currentTimeMillis());

            if (task.getStatus() == TaskStatus.QUEUED) {
                enqueueQueuedTask(task, System.currentTimeMillis());
            }

            persistState();
            LoggerUtil.log("Updated task '" + task.getName() + "' (" + task.getId() + ").");
        }

        wakeUpScheduler();
        return true;
    }

    public ScheduledTask cloneTask(String taskId) {
        synchronized (stateLock) {
            ScheduledTask sourceTask = allTasks.get(taskId);
            if (sourceTask == null) {
                return null;
            }

            ensureCapacityLocked();
            long now = System.currentTimeMillis();
            ScheduledTask clonedTask = sourceTask.createManualCloneTask(now);
            allTasks.put(clonedTask.getId(), clonedTask);
            enqueueQueuedTask(clonedTask, now);
            persistState();

            LoggerUtil.log(
                    "Created a new task from '" + sourceTask.getName() + "' as '" + clonedTask.getName() + "'."
            );
            wakeUpScheduler();
            return clonedTask.copy();
        }
    }

    public boolean pauseTask(String taskId) {
        synchronized (stateLock) {
            ScheduledTask task = allTasks.get(taskId);
            if (task == null || task.getStatus().isTerminal() || task.getStatus() == TaskStatus.PAUSED) {
                return false;
            }

            long now = System.currentTimeMillis();
            if (task.equals(currentTask)) {
                task.markPaused(now);
                currentTask = null;
            } else if (task.getStatus() == TaskStatus.QUEUED) {
                removeFromQueues(task);
                task.markPaused(now);
            } else {
                return false;
            }

            persistState();
            LoggerUtil.log("Paused task '" + task.getName() + "'.");
        }

        wakeUpScheduler();
        return true;
    }

    public boolean resumeTask(String taskId) {
        synchronized (stateLock) {
            ScheduledTask task = allTasks.get(taskId);
            if (task == null || task.getStatus() != TaskStatus.PAUSED) {
                return false;
            }

            long now = System.currentTimeMillis();
            task.markQueued(now);
            enqueueQueuedTask(task, now);
            persistState();
            LoggerUtil.log("Resumed task '" + task.getName() + "'.");
        }

        wakeUpScheduler();
        return true;
    }

    public boolean cancelTask(String taskId) {
        synchronized (stateLock) {
            ScheduledTask task = allTasks.get(taskId);
            if (task == null || task.getStatus().isTerminal()) {
                return false;
            }

            long now = System.currentTimeMillis();
            removeFromQueues(task);

            if (task.equals(currentTask)) {
                currentTask = null;
            }

            task.markCanceled(now);
            persistState();
            LoggerUtil.log("Canceled task '" + task.getName() + "'.");
        }

        wakeUpScheduler();
        return true;
    }

    public SchedulerSnapshot getSnapshot() {
        synchronized (stateLock) {
            long snapshotTime = System.currentTimeMillis();
            List<ScheduledTask> tasks = new ArrayList<>();
            for (ScheduledTask task : allTasks.values()) {
                tasks.add(task.copy());
            }
            tasks.sort(taskComparatorForViews(snapshotTime));
            ScheduledTask current = currentTask == null ? null : currentTask.copy();
            return new SchedulerSnapshot(
                    tasks,
                    current,
                    buildMetrics(tasks, snapshotTime),
                    policySettings,
                    snapshotTime
            );
        }
    }

    public SchedulerPolicySettings getPolicySettings() {
        synchronized (stateLock) {
            return policySettings;
        }
    }

    public void updatePolicySettings(SchedulerPolicySettings updatedSettings) {
        SchedulerPolicySettings safeSettings = updatedSettings == null
                ? SchedulerPolicySettings.defaults()
                : updatedSettings;

        synchronized (stateLock) {
            this.policySettings = safeSettings;
            persistState();
            LoggerUtil.log("Updated scheduler policy: " + safeSettings.describe() + ".");
        }

        wakeUpScheduler();
    }

    private void runLoop() {
        LoggerUtil.log("Scheduler engine started.");

        while (running) {
            try {
                ScheduledTask taskToRun;
                long idleSleepMs;

                synchronized (stateLock) {
                    long now = System.currentTimeMillis();
                    activateDueTasks(now);
                    expireOverdueTasks(now);

                    if (currentTask == null) {
                        currentTask = pollBestReadyTaskLocked(now);
                        if (currentTask != null) {
                            currentTask.markRunning(now);
                            LoggerUtil.log(
                                    "Running task '" + currentTask.getName()
                                            + "' using " + policySettings.schedulingMode() + "."
                            );
                            persistState();
                        }
                    } else {
                        ScheduledTask nextTask = peekBestReadyTaskLocked(now);
                        if (nextTask != null && compareForDispatch(nextTask, currentTask, now) < 0) {
                            ScheduledTask higherPriority = pollBestReadyTaskLocked(now);
                            if (higherPriority != null) {
                                String reason = explainSchedulingDecision(higherPriority, currentTask, now);
                                currentTask.recordPreemption(now);
                                readyQueue.offer(currentTask);
                                LoggerUtil.log(
                                        "Preempted task '" + currentTask.getName()
                                                + "' for '" + higherPriority.getName()
                                                + "' due to " + reason + "."
                                );
                                currentTask = higherPriority;
                                currentTask.markRunning(now);
                                persistState();
                            }
                        }
                    }

                    taskToRun = currentTask;
                    idleSleepMs = computeIdleSleepLocked(now);
                }

                if (taskToRun == null) {
                    Thread.sleep(idleSleepMs);
                    continue;
                }

                Thread.sleep(TICK_MS);

                synchronized (stateLock) {
                    if (currentTask == null
                            || !currentTask.equals(taskToRun)
                            || currentTask.getStatus() != TaskStatus.RUNNING) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    currentTask.consumeTimeSlice(TICK_MS, now);

                    if (now > currentTask.getDeadlineEpochMs() && currentTask.getRemainingDurationMs() > 0L) {
                        handleMissedTaskLocked(
                                currentTask,
                                now,
                                "Task '" + currentTask.getName() + "' missed its deadline while running."
                        );
                        persistState();
                        continue;
                    }

                    if (currentTask.getRemainingDurationMs() <= 0L) {
                        ScheduledTask completedTask = currentTask;
                        completedTask.markCompleted(now);
                        LoggerUtil.log("Task '" + completedTask.getName() + "' completed.");

                        currentTask = null;
                        scheduleNextOccurrenceIfNeeded(completedTask, now);
                        persistState();
                    }
                }
            } catch (InterruptedException interruptedException) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception exception) {
                LoggerUtil.log("Scheduler engine error: " + exception.getMessage());
            }
        }

        synchronized (stateLock) {
            if (currentTask != null && currentTask.getStatus() == TaskStatus.RUNNING) {
                currentTask.markQueued(System.currentTimeMillis());
                enqueueQueuedTask(currentTask, System.currentTimeMillis());
                currentTask = null;
            }
            persistState();
        }

        LoggerUtil.log("Scheduler engine stopped.");
    }

    private void restoreState() {
        List<ScheduledTask> restoredTasks = stateStore.loadTasks();
        long now = System.currentTimeMillis();

        synchronized (stateLock) {
            allTasks.clear();
            readyQueue.clear();
            deferredQueue.clear();
            currentTask = null;

            for (ScheduledTask task : restoredTasks) {
                task.prepareForRestore(now);
                allTasks.put(task.getId(), task);

                if (task.getStatus() == TaskStatus.QUEUED) {
                    enqueueQueuedTask(task, now);
                }
            }
        }

        if (restoredTasks.isEmpty()) {
            LoggerUtil.log("No persisted task state found. Starting with an empty queue.");
        } else {
            LoggerUtil.log("Loaded " + restoredTasks.size() + " task(s) from " + stateStore.getStateFile() + ".");
        }
        LoggerUtil.log("Loaded scheduler policy from " + stateStore.getSettingsFile() + ": " + policySettings.describe() + ".");
    }

    private void activateDueTasks(long now) {
        while (!deferredQueue.isEmpty()) {
            ScheduledTask nextTask = deferredQueue.peek();
            if (nextTask == null || nextTask.getReadyAtEpochMs() > now) {
                break;
            }

            deferredQueue.poll();
            if (nextTask.getStatus() == TaskStatus.QUEUED) {
                readyQueue.offer(nextTask);
                LoggerUtil.log(
                        "Task '" + nextTask.getName() + "' became ready at "
                                + Instant.ofEpochMilli(nextTask.getReadyAtEpochMs()) + "."
                );
            }
        }
    }

    private void expireOverdueTasks(long now) {
        List<ScheduledTask> overdueTasks = new ArrayList<>();
        for (ScheduledTask task : allTasks.values()) {
            if (task.isTerminal()) {
                continue;
            }
            if (task.equals(currentTask)) {
                continue;
            }
            if (now > task.getDeadlineEpochMs()) {
                overdueTasks.add(task);
            }
        }

        for (ScheduledTask overdueTask : overdueTasks) {
            handleMissedTaskLocked(
                    overdueTask,
                    now,
                    "Task '" + overdueTask.getName() + "' expired before it could finish."
            );
        }
    }

    private void handleMissedTaskLocked(ScheduledTask task, long now, String message) {
        if (task == null || task.isTerminal()) {
            return;
        }

        removeFromQueues(task);
        if (task.equals(currentTask)) {
            currentTask = null;
        }

        task.markMissed(now);
        LoggerUtil.log(message);
        scheduleAutomaticRetryIfNeededLocked(task, now);
    }

    private void scheduleAutomaticRetryIfNeededLocked(ScheduledTask task, long now) {
        if (!policySettings.autoRetryEnabled()) {
            return;
        }
        if (!task.hasRetryRemaining()) {
            return;
        }
        if (allTasks.size() >= maxTasks) {
            LoggerUtil.log(
                    "Automatic retry for '" + task.getName() + "' was skipped because the task limit was reached."
            );
            return;
        }

        ScheduledTask retryTask = task.createAutomaticRetryTask(now);
        if (retryTask == null) {
            return;
        }

        allTasks.put(retryTask.getId(), retryTask);
        enqueueQueuedTask(retryTask, now);
        LoggerUtil.log(
                "Queued automatic retry " + retryTask.getRetryAttempt() + "/" + retryTask.getRetryLimit()
                        + " for '" + task.getName()
                        + "' at " + Instant.ofEpochMilli(retryTask.getReadyAtEpochMs()) + "."
        );
    }

    private void scheduleNextOccurrenceIfNeeded(ScheduledTask completedTask, long now) {
        if (!completedTask.hasFutureOccurrence()) {
            return;
        }

        if (allTasks.size() >= maxTasks) {
            LoggerUtil.log(
                    "Recurring follow-up for '" + completedTask.getName() + "' was skipped because the task limit was reached."
            );
            return;
        }

        ScheduledTask nextOccurrence = completedTask.createNextOccurrence(now);
        if (nextOccurrence == null) {
            return;
        }

        allTasks.put(nextOccurrence.getId(), nextOccurrence);
        enqueueQueuedTask(nextOccurrence, now);
        LoggerUtil.log(
                "Scheduled next occurrence for '" + completedTask.getName()
                        + "' (" + nextOccurrence.getOccurrenceIndex() + "/" + nextOccurrence.getRecurrenceLimit()
                        + ") at " + Instant.ofEpochMilli(nextOccurrence.getReadyAtEpochMs()) + "."
        );
    }


private SchedulerMetrics buildMetrics(List<ScheduledTask> tasks, long now) {
    int queued = 0;
    int deferred = 0;
    int runningCount = 0;
    int paused = 0;
    int completed = 0;
    int missed = 0;
    int canceled = 0;
    int atRisk = 0;
    int retryTasks = 0;
    int preemptions = 0;

    long totalExecuted = 0L;
    int finishedLikeTasks = 0;

    long totalWait = 0L;
    int startedTasks = 0;

    for (ScheduledTask task : tasks) {
        preemptions += task.getPreemptionCount();

        if (task.isAtRisk(policySettings, now)) {
            atRisk++;
        }
        if (task.getRetryAttempt() > 0) {
            retryTasks++;
        }
        if (task.getFirstStartedAt() > 0L) {
            totalWait += task.getFirstStartWaitMs();
            startedTasks++;
        }

        switch (task.getStatus()) {
            case QUEUED -> {
                if (task.isDeferred(now)) {
                    deferred++;
                } else {
                    queued++;
                }
            }
            case RUNNING -> runningCount++;
            case PAUSED -> paused++;
            case COMPLETED -> {
                completed++;
                totalExecuted += task.getExecutedDurationMs();
                finishedLikeTasks++;
            }
            case MISSED -> {
                missed++;
                totalExecuted += task.getExecutedDurationMs();
                finishedLikeTasks++;
            }
            case CANCELED -> {
                canceled++;
                totalExecuted += task.getExecutedDurationMs();
                finishedLikeTasks++;
            }
        }
    }

    int activeTasks = queued + deferred + runningCount + paused;
    int historyTasks = completed + missed + canceled;
    long averageExecuted = finishedLikeTasks == 0 ? 0L : totalExecuted / finishedLikeTasks;
    long averageWait = startedTasks == 0 ? 0L : totalWait / startedTasks;
    int deadlineOutcomes = completed + missed;
    int onTimeRate = deadlineOutcomes == 0 ? 100 : (int) Math.round((completed * 100.0) / deadlineOutcomes);

    return new SchedulerMetrics(
            tasks.size(),
            activeTasks,
            historyTasks,
            queued,
            deferred,
            runningCount,
            paused,
            completed,
            missed,
            canceled,
            atRisk,
            retryTasks,
            averageExecuted,
            averageWait,
            onTimeRate,
            preemptions
    );
}

    private void enqueueQueuedTask(ScheduledTask task, long now) {
        if (task == null || task.getStatus() != TaskStatus.QUEUED) {
            return;
        }
        removeFromQueues(task);
        if (task.getReadyAtEpochMs() > now) {
            deferredQueue.offer(task);
        } else {
            readyQueue.offer(task);
        }
    }

    private void removeFromQueues(ScheduledTask task) {
        if (task == null) {
            return;
        }
        readyQueue.remove(task);
        deferredQueue.remove(task);
    }

    private ScheduledTask peekBestReadyTaskLocked(long now) {
        ScheduledTask best = null;
        for (ScheduledTask candidate : readyQueue) {
            if (candidate.getStatus() != TaskStatus.QUEUED) {
                continue;
            }
            if (candidate.isDeferred(now)) {
                continue;
            }
            if (best == null || compareForDispatch(candidate, best, now) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private ScheduledTask pollBestReadyTaskLocked(long now) {
        ScheduledTask best = peekBestReadyTaskLocked(now);
        if (best != null) {
            readyQueue.remove(best);
        }
        return best;
    }

    private int compareForDispatch(ScheduledTask left, ScheduledTask right, long now) {
        switch (policySettings.schedulingMode()) {
            case PRIORITY_FIRST -> {
                int effectivePriorityCompare = Integer.compare(
                        left.getEffectivePriority(policySettings, now),
                        right.getEffectivePriority(policySettings, now)
                );
                if (effectivePriorityCompare != 0) {
                    return effectivePriorityCompare;
                }

                int deadlineCompare = Long.compare(left.getDeadlineEpochMs(), right.getDeadlineEpochMs());
                if (deadlineCompare != 0) {
                    return deadlineCompare;
                }
            }
            case DEADLINE_FIRST -> {
                int slackCompare = Long.compare(left.getSlackMs(now), right.getSlackMs(now));
                if (slackCompare != 0) {
                    return slackCompare;
                }

                int effectivePriorityCompare = Integer.compare(
                        left.getEffectivePriority(policySettings, now),
                        right.getEffectivePriority(policySettings, now)
                );
                if (effectivePriorityCompare != 0) {
                    return effectivePriorityCompare;
                }
            }
            case ADAPTIVE -> {
                boolean leftAtRisk = left.isAtRisk(policySettings, now);
                boolean rightAtRisk = right.isAtRisk(policySettings, now);
                if (leftAtRisk != rightAtRisk) {
                    return leftAtRisk ? -1 : 1;
                }

                int effectivePriorityCompare = Integer.compare(
                        left.getEffectivePriority(policySettings, now),
                        right.getEffectivePriority(policySettings, now)
                );
                if (effectivePriorityCompare != 0) {
                    return effectivePriorityCompare;
                }

                int slackCompare = Long.compare(left.getSlackMs(now), right.getSlackMs(now));
                if (slackCompare != 0) {
                    return slackCompare;
                }
            }
        }

        int priorityCompare = Integer.compare(left.getPriority(), right.getPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        int deadlineCompare = Long.compare(left.getDeadlineEpochMs(), right.getDeadlineEpochMs());
        if (deadlineCompare != 0) {
            return deadlineCompare;
        }

        int readyCompare = Long.compare(left.getReadyAtEpochMs(), right.getReadyAtEpochMs());
        if (readyCompare != 0) {
            return readyCompare;
        }

        return Long.compare(left.getCreatedAt(), right.getCreatedAt());
    }

    private String explainSchedulingDecision(ScheduledTask favored, ScheduledTask displaced, long now) {
        if (policySettings.schedulingMode() == SchedulingMode.ADAPTIVE) {
            boolean favoredAtRisk = favored.isAtRisk(policySettings, now);
            boolean displacedAtRisk = displaced.isAtRisk(policySettings, now);
            if (favoredAtRisk && !displacedAtRisk) {
                return "deadline urgency";
            }
        }

        int favoredEffectivePriority = favored.getEffectivePriority(policySettings, now);
        int displacedEffectivePriority = displaced.getEffectivePriority(policySettings, now);
        if (favoredEffectivePriority < displacedEffectivePriority) {
            return "effective priority " + favoredEffectivePriority + " over " + displacedEffectivePriority;
        }

        if (favored.getSlackMs(now) < displaced.getSlackMs(now)) {
            return "earlier deadline slack";
        }

        return "the selected " + policySettings.schedulingMode() + " policy";
    }

    private long computeIdleSleepLocked(long now) {
        if (!readyQueue.isEmpty()) {
            return 25L;
        }

        ScheduledTask nextDeferred = deferredQueue.peek();
        if (nextDeferred == null) {
            return 150L;
        }

        long waitTime = nextDeferred.getReadyAtEpochMs() - now;
        if (waitTime <= 25L) {
            return 25L;
        }
        return Math.min(250L, waitTime);
    }

    private void persistState() {
        stateStore.saveTasks(allTasks.values());
        stateStore.savePolicySettings(policySettings);
    }

    private void wakeUpScheduler() {
        Thread thread = schedulerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private Comparator<ScheduledTask> taskComparatorForViews(long now) {
        return (left, right) -> {
            int statusCompare = Integer.compare(statusSortOrder(left.getStatus()), statusSortOrder(right.getStatus()));
            if (statusCompare != 0) {
                return statusCompare;
            }

            int deferredCompare = Integer.compare(left.isDeferred(now) ? 1 : 0, right.isDeferred(now) ? 1 : 0);
            if (deferredCompare != 0) {
                return deferredCompare;
            }

            int dispatchCompare = compareForDispatch(left, right, now);
            if (dispatchCompare != 0) {
                return dispatchCompare;
            }

            return Long.compare(left.getCreatedAt(), right.getCreatedAt());
        };
    }

    private int statusSortOrder(TaskStatus status) {
        return switch (status) {
            case SCHEDULED -> 0;
            case RUNNING -> 0;
            case QUEUED -> 1;
            case PAUSED -> 2;
            case COMPLETED -> 3;
            case MISSED -> 4;
            case CANCELED -> 5;
        };
    }

    private void ensureCapacityLocked() {
        if (allTasks.size() >= maxTasks) {
            throw new IllegalStateException("Maximum task limit reached (" + maxTasks + ").");
        }
    }

    private void validateTaskConfiguration(TaskConfiguration configuration, long minDeadlineBase) {
        long now = System.currentTimeMillis();

        if (configuration == null) {
            throw new IllegalArgumentException("Task configuration is required.");
        }
        if (configuration.name() == null || configuration.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be empty.");
        }
        if (configuration.priority() < 1 || configuration.priority() > 10) {
            throw new IllegalArgumentException("Priority must be between 1 and 10.");
        }
        if (configuration.durationMs() <= 0L) {
            throw new IllegalArgumentException("Duration must be greater than zero.");
        }
        if (configuration.deadlineEpochMs() <= now) {
            throw new IllegalArgumentException("Deadline must be in the future.");
        }
        if (configuration.deadlineEpochMs() <= minDeadlineBase) {
            throw new IllegalArgumentException("Deadline must be after the task is eligible to run.");
        }
        if (configuration.recurrenceLimit() < 1) {
            throw new IllegalArgumentException("Occurrence count must be at least 1.");
        }
        if (configuration.recurrenceLimit() > 1 && configuration.recurrenceIntervalMs() <= 0L) {
            throw new IllegalArgumentException("Recurring tasks need a positive repeat-after-completion interval.");
        }
        if (configuration.retryLimit() < 0) {
            throw new IllegalArgumentException("Retry count cannot be negative.");
        }
        if (configuration.retryLimit() > 0 && configuration.retryBackoffMs() < 100L) {
            throw new IllegalArgumentException("Retry backoff must be at least 100 ms when retries are enabled.");
        }
    }
}
