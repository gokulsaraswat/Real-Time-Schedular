package scheduler;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class TaskHistoryTableModel extends AbstractTableModel {
    private final String[] columns = {
            "Finished At", "Task", "Outcome", "Category", "Priority", "Executed (ms)", "Wait (ms)", "Preemptions", "Retry", "Series"
    };

    private List<ScheduledTask> tasks = new ArrayList<>();

    public void setTasks(List<ScheduledTask> tasks) {
        this.tasks = new ArrayList<>(tasks);
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
            case 0, 5, 6 -> Long.class;
            case 4, 7 -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ScheduledTask task = tasks.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> task.getFinishedAt();
            case 1 -> task.getName();
            case 2 -> task.getStatus().name();
            case 3 -> task.getCategory().isBlank() ? "-" : task.getCategory();
            case 4 -> task.getPriority();
            case 5 -> task.getExecutedDurationMs();
            case 6 -> task.getFirstStartWaitMs();
            case 7 -> task.getPreemptionCount();
            case 8 -> task.getRetrySummary();
            case 9 -> task.getSeriesId();
            default -> "";
        };
    }
}
