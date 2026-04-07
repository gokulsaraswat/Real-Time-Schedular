package scheduler;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class TaskTableModel extends AbstractTableModel {
    private final String[] columns = {
            "Name", "Category", "Priority", "Eff Pri", "Status", "Ready At", "Deadline", "Slack (ms)", "Wait (ms)", "Retry", "Progress"
    };

    private List<ScheduledTask> tasks = new ArrayList<>();
    private SchedulerPolicySettings policySettings = SchedulerPolicySettings.defaults();
    private long snapshotTime = System.currentTimeMillis();

    public void setTasks(List<ScheduledTask> tasks, SchedulerPolicySettings policySettings, long snapshotTime) {
        this.tasks = new ArrayList<>(tasks);
        this.policySettings = policySettings == null ? SchedulerPolicySettings.defaults() : policySettings;
        this.snapshotTime = snapshotTime;
        fireTableDataChanged();
    }

    public ScheduledTask getTaskAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(rowIndex);
    }

    public int findRowByTaskId(String taskId) {
        if (taskId == null) {
            return -1;
        }
        for (int index = 0; index < tasks.size(); index++) {
            if (taskId.equals(tasks.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return tasks.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 2, 3, 10 -> Integer.class;
            case 5, 6, 7, 8 -> Long.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ScheduledTask task = tasks.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> task.getName();
            case 1 -> task.getCategory().isBlank() ? "-" : task.getCategory();
            case 2 -> task.getPriority();
            case 3 -> task.getEffectivePriority(policySettings, snapshotTime);
            case 4 -> task.getStatus().name();
            case 5 -> task.getReadyAtEpochMs();
            case 6 -> task.getDeadlineEpochMs();
            case 7 -> task.getSlackMs(snapshotTime);
            case 8 -> task.getQueueWaitMs(snapshotTime);
            case 9 -> task.getRetrySummary();
            case 10 -> task.getProgressPercent();
            default -> "";
        };
    }
}
