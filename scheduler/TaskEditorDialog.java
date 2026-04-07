package scheduler;

import javax.swing.JOptionPane;
import java.awt.Component;

public final class TaskEditorDialog {
    private TaskEditorDialog() {
    }

    public static TaskRequest showDialog(Component parent, ScheduledTask task) {
        TaskFormPanel formPanel = new TaskFormPanel("Edit Task");
        formPanel.loadFromTask(task);

        int choice = JOptionPane.showConfirmDialog(
                parent,
                formPanel,
                "Edit Task",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        return formPanel.toTaskRequest();
    }
}
