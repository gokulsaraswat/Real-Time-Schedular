package scheduler;

import java.io.Serial;
import java.io.Serializable;

public record SchedulerPolicySettings(
        SchedulingMode schedulingMode,
        boolean agingEnabled,
        long agingQuantumMs,
        long deadlineUrgencyWindowMs,
        boolean autoRetryEnabled
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public SchedulerPolicySettings {
        schedulingMode = schedulingMode == null ? SchedulingMode.ADAPTIVE : schedulingMode;
        agingQuantumMs = Math.max(250L, agingQuantumMs);
        deadlineUrgencyWindowMs = Math.max(250L, deadlineUrgencyWindowMs);
    }

    public static SchedulerPolicySettings defaults() {
        return new SchedulerPolicySettings(
                SchedulingMode.ADAPTIVE,
                true,
                2_000L,
                1_500L,
                true
        );
    }

    public String describe() {
        return schedulingMode
                + " • aging " + (agingEnabled ? ("on/" + agingQuantumMs + " ms") : "off")
                + " • urgency window " + deadlineUrgencyWindowMs + " ms"
                + " • auto-retry " + (autoRetryEnabled ? "on" : "off");
    }
}
