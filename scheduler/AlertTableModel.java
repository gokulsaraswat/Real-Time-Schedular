package scheduler;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class AlertTableModel extends AbstractTableModel {
    private final String[] columns = {"Time", "Level", "Task", "Message"};
    private List<SchedulerAlert> alerts = new ArrayList<>();

    public void setAlerts(List<SchedulerAlert> alerts) {
        this.alerts = new ArrayList<>(alerts);
        fireTableDataChanged();
    }

    public SchedulerAlert getAlertAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= alerts.size()) {
            return null;
        }
        return alerts.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return alerts.size();
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
        return columnIndex == 0 ? Long.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SchedulerAlert alert = alerts.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> alert.createdAt();
            case 1 -> alert.severity().label();
            case 2 -> alert.displayTaskName();
            case 3 -> alert.message();
            default -> "";
        };
    }
}
