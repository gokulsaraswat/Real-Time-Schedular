package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NotificationCenterPanel extends JPanel {
    private static final int MAX_ALERTS = 200;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AlertTableModel tableModel = new AlertTableModel();
    private final JTable table = new JTable(tableModel);
    private final List<SchedulerAlert> alerts = new ArrayList<>();

    private final javax.swing.JLabel unreadLabel = new javax.swing.JLabel("Unread: 0");
    private final JCheckBox beepOnErrorsCheckBox = new JCheckBox("Beep on errors", true);
    private int unreadCount = 0;
    private Consumer<SchedulerAlert> openHandler;

    public NotificationCenterPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Notification Center"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton markReadButton = new JButton("Mark All Read");
        JButton clearButton = new JButton("Clear Alerts");
        markReadButton.addActionListener(event -> markAllRead());
        clearButton.addActionListener(event -> clearAlerts());

        toolbar.add(unreadLabel);
        toolbar.add(beepOnErrorsCheckBox);
        toolbar.add(markReadButton);
        toolbar.add(clearButton);
        add(toolbar, BorderLayout.NORTH);

        table.setAutoCreateRowSorter(true);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new AlertTextRenderer());
        table.setDefaultRenderer(Long.class, new AlertTextRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(520);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedAlert();
                }
            }
        });
        table.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "open-alert");
        table.getActionMap().put("open-alert", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openSelectedAlert();
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setOpenHandler(Consumer<SchedulerAlert> openHandler) {
        this.openHandler = openHandler;
    }

    public void addAlert(SchedulerAlert alert) {
        if (alert == null) {
            return;
        }

        alerts.add(0, alert);
        while (alerts.size() > MAX_ALERTS) {
            alerts.remove(alerts.size() - 1);
        }
        unreadCount = Math.min(MAX_ALERTS, unreadCount + 1);
        tableModel.setAlerts(alerts);
        updateUnreadLabel();

        if (beepOnErrorsCheckBox.isSelected() && alert.severity() == AlertSeverity.ERROR) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public void markAllRead() {
        unreadCount = 0;
        updateUnreadLabel();
    }

    public void clearAlerts() {
        alerts.clear();
        unreadCount = 0;
        tableModel.setAlerts(alerts);
        updateUnreadLabel();
    }

    public List<SchedulerAlert> getAlerts() {
        return new ArrayList<>(alerts);
    }

    private void updateUnreadLabel() {
        unreadLabel.setText("Unread: " + unreadCount);
    }

    private void openSelectedAlert() {
        if (openHandler == null) {
            return;
        }

        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        SchedulerAlert alert = tableModel.getAlertAt(modelRow);
        if (alert != null) {
            openHandler.accept(alert);
            SwingUtilities.invokeLater(this::markAllRead);
        }
    }

    private class AlertTextRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            SchedulerAlert alert = tableModel.getAlertAt(modelRow);

            if (column == 0 && value instanceof Long timeValue) {
                setText(TIME_FORMATTER.format(Instant.ofEpochMilli(timeValue)));
            } else {
                setText(value == null ? "" : String.valueOf(value));
            }

            if (!isSelected && alert != null) {
                setBackground(colorFor(alert.severity()));
                setForeground(Color.BLACK);
            } else if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }
            return this;
        }

        private Color colorFor(AlertSeverity severity) {
            return switch (severity) {
                case SUCCESS -> new Color(232, 245, 233);
                case WARNING -> new Color(255, 248, 225);
                case ERROR -> new Color(255, 235, 238);
                case INFO -> Color.WHITE;
            };
        }
    }
}
