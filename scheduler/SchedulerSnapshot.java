package scheduler;

import java.util.List;

public record SchedulerSnapshot(
        List<ScheduledTask> tasks,
        ScheduledTask currentTask,
        SchedulerMetrics metrics,
        SchedulerPolicySettings policySettings,
        long generatedAt
) {
}
