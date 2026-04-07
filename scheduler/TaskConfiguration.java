package scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record TaskConfiguration(
        String name,
        int priority,
        long durationMs,
        long deadlineEpochMs,
        String category,
        List<String> tags,
        String notes,
        long recurrenceIntervalMs,
        int recurrenceLimit,
        int retryLimit,
        long retryBackoffMs
) {
    public TaskConfiguration {
        category = normalizeOptional(category);
        notes = normalizeOptional(notes);
        tags = normalizeTags(tags);
        recurrenceIntervalMs = Math.max(0L, recurrenceIntervalMs);
        recurrenceLimit = Math.max(1, recurrenceLimit);
        retryLimit = Math.max(0, retryLimit);
        retryBackoffMs = retryLimit == 0 ? 0L : Math.max(100L, retryBackoffMs);

        if (recurrenceIntervalMs == 0L) {
            recurrenceLimit = 1;
        }
    }

    public boolean isRecurring() {
        return recurrenceIntervalMs > 0L && recurrenceLimit > 1;
    }

    public boolean supportsAutoRetry() {
        return retryLimit > 0;
    }

    public String tagsDisplay() {
        return tags.isEmpty() ? "-" : String.join(", ", tags);
    }

    public static List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return List.of();
        }

        String[] parts = rawTags.split(",");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalizeOptional(part);
            if (!normalized.isEmpty() && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    public static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeTags(List<String> input) {
        List<String> values = new ArrayList<>();
        if (input == null) {
            return values;
        }

        for (String tag : input) {
            String normalized = normalizeOptional(tag);
            if (!normalized.isEmpty()) {
                String duplicateKey = normalized.toLowerCase(Locale.ROOT);
                boolean duplicate = values.stream()
                        .map(existing -> existing.toLowerCase(Locale.ROOT))
                        .anyMatch(duplicateKey::equals);
                if (!duplicate) {
                    values.add(normalized);
                }
            }
        }
        return values;
    }

    public static TaskConfiguration fromTask(ScheduledTask task) {
        return new TaskConfiguration(
                task.getName(),
                task.getPriority(),
                task.getTotalDurationMs(),
                task.getDeadlineEpochMs(),
                task.getCategory(),
                task.getTags(),
                task.getNotes(),
                task.getRecurrenceIntervalMs(),
                task.getRecurrenceLimit(),
                task.getRetryLimit(),
                task.getRetryBackoffMs()
        );
    }
}
