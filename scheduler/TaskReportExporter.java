package scheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class TaskReportExporter {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TaskReportExporter() {
    }

    public static void exportCsv(List<ScheduledTask> tasks, Path targetFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("id,seriesId,parentTaskId,name,tags,occurrence,retryAttempt,maxRetries,priority,status,scheduledStart,deadline,totalDurationMs,remainingDurationMs,progressPercent,recurrenceIntervalMs,retryBackoffMs,preemptions,createdAt,updatedAt,finishedAt")
                .append(System.lineSeparator());

        for (ScheduledTask task : tasks) {
            builder.append(csv(task.getId())).append(',')
                    .append(csv(task.getSeriesId())).append(',')
                    .append(csv("")) // Parent task concept introduced in later patches
                    .append(',')
                    .append(csv(task.getName())).append(',')
                    .append(csv(String.join(",", task.getTags()))).append(',')
                    .append(task.getOccurrenceIndex()).append(',')
                    .append(0).append(',')
                    .append(0).append(',')
                    .append(task.getPriority()).append(',')
                    .append(csv(task.getStatus().name())).append(',')
                    .append(csv(formatTime(task.getReadyAtEpochMs()))).append(',')
                    .append(csv(formatTime(task.getDeadlineEpochMs()))).append(',')
                    .append(task.getTotalDurationMs()).append(',')
                    .append(task.getRemainingDurationMs()).append(',')
                    .append(task.getProgressPercent()).append(',')
                    .append(task.getRecurrenceIntervalMs()).append(',')
                    .append(0).append(',')
                    .append(task.getPreemptionCount()).append(',')
                    .append(csv(formatTime(task.getCreatedAt()))).append(',')
                    .append(csv(formatTime(task.getUpdatedAt()))).append(',')
                    .append(csv(task.getFinishedAt() > 0L ? formatTime(task.getFinishedAt()) : ""))
                    .append(System.lineSeparator());
        }

        Files.writeString(targetFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static String formatTime(long epochMs) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs));
    }
}
