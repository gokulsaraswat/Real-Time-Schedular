package scheduler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class SchedulerDashboard extends JFrame {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SchedulerEngine engine;
    private final Path dataDirectory;

    private final TaskProgressPanel progressPanel = new TaskProgressPanel();
    private final PerformanceMetricsPanel metricsPanel = new PerformanceMetricsPanel();
    private final NotificationCenterPanel notificationPanel = new NotificationCenterPanel();
    private final TaskChartPanel chartPanel = new TaskChartPanel();

    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable taskTable = new JTable(tableModel);
    private final TaskHistoryTableModel historyTableModel = new TaskHistoryTableModel();
    private final JTable historyTable = new JTable(historyTableModel);
    private final TaskDetailsPanel liveDetailsPanel = new TaskDetailsPanel("Selected Active Task");
    private final TaskDetailsPanel historyDetailsPanel = new TaskDetailsPanel("Selected History Entry");
    private final JTextArea logArea = new JTextArea();

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> statusFilterCombo = new JComboBox<>();
    private final JComboBox<String> categoryFilterCombo = new JComboBox<>();
    private final JSpinner maxPrioritySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 10, 1));
    private final JCheckBox atRiskOnlyCheckBox = new JCheckBox("At-risk only");

    private final JTextField historySearchField = new JTextField();
    private final JComboBox<String> historyStatusFilterCombo = new JComboBox<>();
    private final JComboBox<String> historyCategoryFilterCombo = new JComboBox<>();
    private final JCheckBox historySpecialOnlyCheckBox = new JCheckBox("Recurring / retry only");

    private final JComboBox<SchedulingMode> schedulingModeCombo = new JComboBox<>(SchedulingMode.values());
    private final JCheckBox agingCheckBox = new JCheckBox("Enable aging");
    private final JSpinner agingQuantumSpinner = new JSpinner(new SpinnerNumberModel(2000L, 250L, 600_000L, 250L));
    private final JSpinner urgencyWindowSpinner = new JSpinner(new SpinnerNumberModel(1500L, 250L, 600_000L, 250L));
    private final JCheckBox autoRetryCheckBox = new JCheckBox("Enable auto retry for missed tasks");

    private final JTabbedPane workspaceTabs = new JTabbedPane();
    private JPanel overviewTab;
    private JPanel historyTab;

    private final JLabel statusLabel;
    private final Timer refreshTimer;

    private final Map<String, TaskStatus> previousStatuses = new HashMap<>();
    private final Map<String, Boolean> previousRiskState = new HashMap<>();
    private boolean alertStateInitialized = false;

    private int lastRenderedLogCount = 0;
    private String pendingActiveSelectionTaskId;
    private String pendingHistorySelectionTaskId;
    private SchedulerSnapshot lastSnapshot;

    public SchedulerDashboard(SchedulerEngine engine, Path dataDirectory) {
        this.engine = engine;
        this.dataDirectory = dataDirectory;
        this.statusLabel = new JLabel("Local state folder: " + dataDirectory);

        setTitle("Real-Time Scheduler • Phase 4");
        setSize(1500, 980);
        setMinimumSize(new Dimension(1280, 780));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        configureFilters();
        configureActiveTable();
        configureHistoryTable();
        configureNotifications();
        updatePolicyControls(engine.getPolicySettings());

        add(progressPanel, BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdownAndClose();
            }
        });

        refreshTimer = new Timer(250, event -> refreshUi());
        refreshTimer.start();
        refreshUi();
    }

    private void configureFilters() {
        statusFilterCombo.addItem("All");
        statusFilterCombo.addItem(TaskStatus.QUEUED.name());
        statusFilterCombo.addItem(TaskStatus.RUNNING.name());
        statusFilterCombo.addItem(TaskStatus.PAUSED.name());

        categoryFilterCombo.addItem("All");

        historyStatusFilterCombo.addItem("All");
        historyStatusFilterCombo.addItem(TaskStatus.COMPLETED.name());
        historyStatusFilterCombo.addItem(TaskStatus.MISSED.name());
        historyStatusFilterCombo.addItem(TaskStatus.CANCELED.name());
        historyCategoryFilterCombo.addItem("All");

        agingCheckBox.addActionListener(event -> updatePolicyControlState());
    }

    private void configureNotifications() {
        notificationPanel.setOpenHandler(alert -> {
            if (!alert.taskId().isBlank()) {
                focusTask(alert.taskId());
            }
        });
    }

    private void configureActiveTable() {
        taskTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        taskTable.setAutoCreateRowSorter(true);
        taskTable.setFillsViewportHeight(true);
        taskTable.setRowHeight(24);
        taskTable.getSelectionModel().addListSelectionListener(this::handleActiveSelectionChanged);
        taskTable.setDefaultRenderer(Object.class, new ActiveTaskTextRenderer());
        taskTable.setDefaultRenderer(Integer.class, new ActiveTaskTextRenderer());
        taskTable.setDefaultRenderer(Long.class, new ActiveTaskTextRenderer());
        taskTable.getColumnModel().getColumn(10).setCellRenderer(new ProgressBarRenderer());

        taskTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        taskTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(90);
        taskTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        taskTable.getColumnModel().getColumn(8).setPreferredWidth(90);
        taskTable.getColumnModel().getColumn(9).setPreferredWidth(120);
        taskTable.getColumnModel().getColumn(10).setPreferredWidth(120);
    }

    private void configureHistoryTable() {
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setAutoCreateRowSorter(true);
        historyTable.setFillsViewportHeight(true);
        historyTable.setRowHeight(24);
        historyTable.getSelectionModel().addListSelectionListener(this::handleHistorySelectionChanged);
        historyTable.setDefaultRenderer(Object.class, new HistoryTaskTextRenderer());
        historyTable.setDefaultRenderer(Integer.class, new HistoryTaskTextRenderer());
        historyTable.setDefaultRenderer(Long.class, new HistoryTaskTextRenderer());

        historyTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(105);
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        historyTable.getColumnModel().getColumn(8).setPreferredWidth(120);
    }

    private JSplitPane buildCenterPanel() {
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.34);

        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        JPanel leftTop = new JPanel(new BorderLayout(8, 8));
        leftTop.add(buildPolicyPanel(), BorderLayout.NORTH);
        leftTop.add(metricsPanel, BorderLayout.CENTER);
        leftPanel.add(leftTop, BorderLayout.NORTH);

        JTabbedPane supportTabs = new JTabbedPane();
        supportTabs.addTab("Alerts", notificationPanel);
        supportTabs.addTab("Logs", buildLogPanel());
        leftPanel.add(supportTabs, BorderLayout.CENTER);

        overviewTab = buildOverviewTab();
        historyTab = buildHistoryTab();
        workspaceTabs.addTab("Active Workspace", overviewTab);
        workspaceTabs.addTab("History", historyTab);

        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(workspaceTabs);
        return mainSplit;
    }

    private JPanel buildOverviewTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JSplitPane tableDetailsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        tableDetailsSplit.setResizeWeight(0.68);

        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        tablePanel.add(buildFilterPanel(), BorderLayout.NORTH);

        JScrollPane tableScrollPane = new JScrollPane(taskTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Active Tasks"));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);

        tableDetailsSplit.setTopComponent(tablePanel);
        tableDetailsSplit.setBottomComponent(liveDetailsPanel);

        JSplitPane contentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        contentSplit.setResizeWeight(0.33);
        contentSplit.setTopComponent(chartPanel);
        contentSplit.setBottomComponent(tableDetailsSplit);

        panel.add(contentSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        tablePanel.add(buildHistoryFilterPanel(), BorderLayout.NORTH);

        JScrollPane historyScrollPane = new JScrollPane(historyTable);
        historyScrollPane.setBorder(BorderFactory.createTitledBorder("Completed / Missed / Canceled"));
        tablePanel.add(historyScrollPane, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.68);
        splitPane.setTopComponent(tablePanel);
        splitPane.setBottomComponent(historyDetailsPanel);

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        logArea.setLineWrap(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Execution Logs"));
        return logScrollPane;
    }

    private JPanel buildPolicyPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 4, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Scheduler Policy"));

        panel.add(new JLabel("Mode"));
        panel.add(new JLabel("Aging quantum (ms)"));
        panel.add(new JLabel("Urgency window (ms)"));
        panel.add(new JLabel(""));

        panel.add(schedulingModeCombo);
        panel.add(agingQuantumSpinner);
        panel.add(urgencyWindowSpinner);

        JButton applyPolicyButton = new JButton("Apply Policy");
        applyPolicyButton.addActionListener(event -> applySchedulerPolicy());
        panel.add(applyPolicyButton);

        panel.add(agingCheckBox);
        panel.add(autoRetryCheckBox);
        panel.add(new JLabel("Adaptive mode boosts urgent tasks."));
        panel.add(new JLabel("Aging reduces starvation for ready tasks."));
        return panel;
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 6, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Active Filters"));

        panel.add(new JLabel("Search"));
        panel.add(new JLabel("Status"));
        panel.add(new JLabel("Category"));
        panel.add(new JLabel("Max Priority"));
        panel.add(new JLabel("Urgency"));
        panel.add(new JLabel(""));

        panel.add(searchField);
        panel.add(statusFilterCombo);
        panel.add(categoryFilterCombo);
        panel.add(maxPrioritySpinner);
        panel.add(atRiskOnlyCheckBox);

        JButton clearButton = new JButton("Clear Filters");
        clearButton.addActionListener(event -> {
            clearActiveFilters();
            refreshUi();
        });
        panel.add(clearButton);
        return panel;
    }

    private JPanel buildHistoryFilterPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 5, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("History Filters"));

        panel.add(new JLabel("Search"));
        panel.add(new JLabel("Outcome"));
        panel.add(new JLabel("Category"));
        panel.add(new JLabel("Special"));
        panel.add(new JLabel(""));

        panel.add(historySearchField);
        panel.add(historyStatusFilterCombo);
        panel.add(historyCategoryFilterCombo);
        panel.add(historySpecialOnlyCheckBox);

        JButton clearButton = new JButton("Clear Filters");
        clearButton.addActionListener(event -> {
            clearHistoryFilters();
            refreshUi();
        });
        panel.add(clearButton);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 8));

        TaskInputPanel inputPanel = new TaskInputPanel(configuration -> {
            try {
                ScheduledTask createdTask = engine.addTask(configuration);
                pendingActiveSelectionTaskId = createdTask.getId();
                workspaceTabs.setSelectedComponent(overviewTab);
                refreshUi();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        exception.getMessage(),
                        "Failed to Add Task",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
        wrapper.add(inputPanel, BorderLayout.CENTER);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton pauseButton = new JButton("Pause Selected");
        JButton resumeButton = new JButton("Resume Selected");
        JButton cancelButton = new JButton("Cancel Selected");
        JButton editButton = new JButton("Edit Selected");
        JButton cloneButton = new JButton("Retry / Clone Selected");
        JButton refreshButton = new JButton("Refresh");
        JButton openFolderButton = new JButton("Open Data Folder");
        JButton exportLogsButton = new JButton("Export Logs");

        pauseButton.addActionListener(event -> performActionOnSelectedTasks(ActionType.PAUSE));
        resumeButton.addActionListener(event -> performActionOnSelectedTasks(ActionType.RESUME));
        cancelButton.addActionListener(event -> performActionOnSelectedTasks(ActionType.CANCEL));
        editButton.addActionListener(event -> performActionOnSelectedTasks(ActionType.EDIT));
        cloneButton.addActionListener(event -> performActionOnSelectedTasks(ActionType.CLONE));
        refreshButton.addActionListener(event -> refreshUi());
        openFolderButton.addActionListener(event -> openDataFolder());
        exportLogsButton.addActionListener(event -> exportLogs());

        actionsPanel.add(pauseButton);
        actionsPanel.add(resumeButton);
        actionsPanel.add(cancelButton);
        actionsPanel.add(editButton);
        actionsPanel.add(cloneButton);
        actionsPanel.add(refreshButton);
        actionsPanel.add(openFolderButton);
        actionsPanel.add(exportLogsButton);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.add(actionsPanel, BorderLayout.NORTH);
        footerPanel.add(statusLabel, BorderLayout.SOUTH);

        wrapper.add(footerPanel, BorderLayout.SOUTH);
        return wrapper;
    }

    private void applySchedulerPolicy() {
        try {
            SchedulerPolicySettings settings = new SchedulerPolicySettings(
                    (SchedulingMode) schedulingModeCombo.getSelectedItem(),
                    agingCheckBox.isSelected(),
                    ((Number) agingQuantumSpinner.getValue()).longValue(),
                    ((Number) urgencyWindowSpinner.getValue()).longValue(),
                    autoRetryCheckBox.isSelected()
            );
            engine.updatePolicySettings(settings);
            updatePolicyControls(settings);
            refreshUi();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Invalid Policy",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updatePolicyControls(SchedulerPolicySettings settings) {
        SchedulerPolicySettings safeSettings = settings == null ? SchedulerPolicySettings.defaults() : settings;
        schedulingModeCombo.setSelectedItem(safeSettings.schedulingMode());
        agingCheckBox.setSelected(safeSettings.agingEnabled());
        agingQuantumSpinner.setValue(safeSettings.agingQuantumMs());
        urgencyWindowSpinner.setValue(safeSettings.deadlineUrgencyWindowMs());
        autoRetryCheckBox.setSelected(safeSettings.autoRetryEnabled());
        updatePolicyControlState();
    }

    private void updatePolicyControlState() {
        agingQuantumSpinner.setEnabled(agingCheckBox.isSelected());
    }

    private void handleActiveSelectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateLiveDetails();
        }
    }

    private void handleHistorySelectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateHistoryDetails();
        }
    }

    private void performActionOnSelectedTasks(ActionType actionType) {
        List<ScheduledTask> selectedTasks = getSelectedTasksFromActiveTable();
        if (selectedTasks.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Select one or more active tasks first.",
                    "No Task Selected",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            switch (actionType) {
                case PAUSE -> applyBulkStateChange(selectedTasks, actionType);
                case RESUME -> applyBulkStateChange(selectedTasks, actionType);
                case CANCEL -> applyBulkStateChange(selectedTasks, actionType);
                case EDIT -> editSelectedTask(requireSingleSelection(selectedTasks));
                case CLONE -> cloneSelectedTask(requireSingleSelection(selectedTasks));
                default -> {
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "Action Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        refreshUi();
    }

    private ScheduledTask requireSingleSelection(List<ScheduledTask> selectedTasks) {
        if (selectedTasks.size() != 1) {
            throw new IllegalStateException("Select exactly one task for this action.");
        }
        return selectedTasks.get(0);
    }

    private void applyBulkStateChange(List<ScheduledTask> selectedTasks, ActionType actionType) {
        int successCount = 0;

        for (ScheduledTask task : selectedTasks) {
            boolean changed = switch (actionType) {
                case PAUSE -> engine.pauseTask(task.getId());
                case RESUME -> engine.resumeTask(task.getId());
                case CANCEL -> engine.cancelTask(task.getId());
                default -> false;
            };
            if (changed) {
                successCount++;
            }
        }

        if (successCount == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "None of the selected tasks could be updated in their current states.",
                    "Action Not Available",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else if (successCount < selectedTasks.size()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Updated " + successCount + " of " + selectedTasks.size() + " selected tasks.",
                    "Partial Update",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private void editSelectedTask(ScheduledTask task) {
        TaskConfiguration configuration = TaskEditDialog.showDialog(this, task);
        if (configuration == null) {
            return;
        }

        boolean updated = engine.updateTask(task.getId(), configuration);
        if (!updated) {
            JOptionPane.showMessageDialog(
                    this,
                    "Only queued or paused tasks can be edited.",
                    "Edit Not Available",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            pendingActiveSelectionTaskId = task.getId();
        }
    }

    private void cloneSelectedTask(ScheduledTask task) {
        ScheduledTask clonedTask = engine.cloneTask(task.getId());
        if (clonedTask == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to create a new copy of the selected task.",
                    "Clone Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        } else {
            pendingActiveSelectionTaskId = clonedTask.getId();
            workspaceTabs.setSelectedComponent(overviewTab);
        }
    }

    private List<ScheduledTask> getSelectedTasksFromActiveTable() {
        int[] selectedRows = taskTable.getSelectedRows();
        List<ScheduledTask> selectedTasks = new ArrayList<>();
        for (int selectedRow : selectedRows) {
            int modelRow = taskTable.convertRowIndexToModel(selectedRow);
            ScheduledTask task = tableModel.getTaskAt(modelRow);
            if (task != null) {
                selectedTasks.add(task);
            }
        }
        return selectedTasks;
    }

    private ScheduledTask getSelectedHistoryTask() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = historyTable.convertRowIndexToModel(selectedRow);
        return historyTableModel.getTaskAt(modelRow);
    }

    private void exportLogs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(Path.of("scheduler_logs.txt").toFile());
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            LoggerUtil.exportToFile(chooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(
                    this,
                    "Logs exported successfully.",
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to export logs: " + exception.getMessage(),
                    "Export Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void openDataFolder() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dataDirectory.toFile());
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Desktop folder open is not supported on this machine.",
                        "Open Folder Unavailable",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to open the local data folder: " + exception.getMessage(),
                    "Open Folder Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void refreshUi() {
        String selectedActiveTaskId = pendingActiveSelectionTaskId;
        if (selectedActiveTaskId == null) {
            List<ScheduledTask> selectedTasks = getSelectedTasksFromActiveTable();
            if (!selectedTasks.isEmpty()) {
                selectedActiveTaskId = selectedTasks.get(0).getId();
            }
        }

        String selectedHistoryTaskId = pendingHistorySelectionTaskId;
        if (selectedHistoryTaskId == null) {
            ScheduledTask selectedHistoryTask = getSelectedHistoryTask();
            if (selectedHistoryTask != null) {
                selectedHistoryTaskId = selectedHistoryTask.getId();
            }
        }

        SchedulerSnapshot snapshot = engine.getSnapshot();
        emitAlertsForSnapshot(snapshot);
        lastSnapshot = snapshot;

        updateCategoryFilterOptions(snapshot.tasks());
        updateHistoryCategoryFilterOptions(snapshot.tasks());

        List<ScheduledTask> activeTasks = applyActiveFilters(snapshot.tasks());
        tableModel.setTasks(activeTasks, snapshot.policySettings(), snapshot.generatedAt());
        restoreActiveSelection(selectedActiveTaskId);
        pendingActiveSelectionTaskId = null;

        List<ScheduledTask> historyTasks = applyHistoryFilters(snapshot.tasks());
        historyTableModel.setTasks(historyTasks);
        restoreHistorySelection(selectedHistoryTaskId);
        pendingHistorySelectionTaskId = null;

        metricsPanel.updateMetrics(snapshot.metrics());
        chartPanel.setTasks(snapshot.tasks());

        if (snapshot.currentTask() != null) {
            progressPanel.updateCurrentTask(snapshot.currentTask(), snapshot.policySettings(), snapshot.generatedAt());
        } else {
            progressPanel.clearProgress();
        }

        updateLogs(LoggerUtil.getLogs());
        updateLiveDetails();
        updateHistoryDetails();

        statusLabel.setText(
                "Active " + snapshot.metrics().activeTasks()
                        + " • History " + snapshot.metrics().historyTasks()
                        + " • Showing " + activeTasks.size() + " live / " + historyTasks.size() + " history"
                        + " • " + snapshot.policySettings().describe()
                        + " • Local state folder: " + dataDirectory
        );
    }

    private void restoreActiveSelection(String selectedTaskId) {
        if (selectedTaskId == null) {
            taskTable.clearSelection();
            return;
        }

        int modelRow = tableModel.findRowByTaskId(selectedTaskId);
        if (modelRow >= 0) {
            int viewRow = taskTable.convertRowIndexToView(modelRow);
            if (viewRow >= 0) {
                taskTable.setRowSelectionInterval(viewRow, viewRow);
                taskTable.scrollRectToVisible(taskTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
        taskTable.clearSelection();
    }

    private void restoreHistorySelection(String selectedTaskId) {
        if (selectedTaskId == null) {
            historyTable.clearSelection();
            return;
        }

        int modelRow = historyTableModel.findRowByTaskId(selectedTaskId);
        if (modelRow >= 0) {
            int viewRow = historyTable.convertRowIndexToView(modelRow);
            if (viewRow >= 0) {
                historyTable.setRowSelectionInterval(viewRow, viewRow);
                historyTable.scrollRectToVisible(historyTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
        historyTable.clearSelection();
    }

    private void emitAlertsForSnapshot(SchedulerSnapshot snapshot) {
        Map<String, TaskStatus> currentStatuses = new HashMap<>();
        Map<String, Boolean> currentRiskState = new HashMap<>();

        for (ScheduledTask task : snapshot.tasks()) {
            currentStatuses.put(task.getId(), task.getStatus());
            currentRiskState.put(task.getId(), task.isAtRisk(snapshot.policySettings(), snapshot.generatedAt()));
        }

        if (!alertStateInitialized) {
            previousStatuses.clear();
            previousStatuses.putAll(currentStatuses);
            previousRiskState.clear();
            previousRiskState.putAll(currentRiskState);
            alertStateInitialized = true;
            return;
        }

        for (ScheduledTask task : snapshot.tasks()) {
            TaskStatus previousStatus = previousStatuses.get(task.getId());
            boolean wasAtRisk = previousRiskState.getOrDefault(task.getId(), false);
            boolean isAtRisk = currentRiskState.getOrDefault(task.getId(), false);

            if (previousStatus == null) {
                if (task.getRetryAttempt() > 0) {
                    notificationPanel.addAlert(new SchedulerAlert(
                            snapshot.generatedAt(),
                            AlertSeverity.WARNING,
                            task.getId(),
                            task.getName(),
                            "Automatic retry " + task.getRetryAttempt() + "/" + task.getRetryLimit()
                                    + " queued for the missed task series."
                    ));
                }
            } else if (previousStatus != task.getStatus()) {
                switch (task.getStatus()) {
                    case COMPLETED -> notificationPanel.addAlert(new SchedulerAlert(
                            snapshot.generatedAt(),
                            AlertSeverity.SUCCESS,
                            task.getId(),
                            task.getName(),
                            "Task completed successfully."
                    ));
                    case MISSED -> notificationPanel.addAlert(new SchedulerAlert(
                            snapshot.generatedAt(),
                            AlertSeverity.ERROR,
                            task.getId(),
                            task.getName(),
                            "Task missed its deadline with " + task.getRemainingDurationMs() + " ms remaining."
                    ));
                    case CANCELED -> notificationPanel.addAlert(new SchedulerAlert(
                            snapshot.generatedAt(),
                            AlertSeverity.WARNING,
                            task.getId(),
                            task.getName(),
                            "Task was canceled."
                    ));
                    case PAUSED -> notificationPanel.addAlert(new SchedulerAlert(
                            snapshot.generatedAt(),
                            AlertSeverity.INFO,
                            task.getId(),
                            task.getName(),
                            "Task was paused."
                    ));
                    case QUEUED -> {
                        if (previousStatus == TaskStatus.PAUSED) {
                            notificationPanel.addAlert(new SchedulerAlert(
                                    snapshot.generatedAt(),
                                    AlertSeverity.INFO,
                                    task.getId(),
                                    task.getName(),
                                    "Task resumed and returned to the ready queue."
                            ));
                        }
                    }
                    default -> {
                    }
                }
            }

            if (!wasAtRisk && isAtRisk && !task.isTerminal()) {
                notificationPanel.addAlert(new SchedulerAlert(
                        snapshot.generatedAt(),
                        AlertSeverity.WARNING,
                        task.getId(),
                        task.getName(),
                        "Task is now at risk. Slack is " + task.getSlackMs(snapshot.generatedAt()) + " ms."
                ));
            }
        }

        previousStatuses.clear();
        previousStatuses.putAll(currentStatuses);
        previousRiskState.clear();
        previousRiskState.putAll(currentRiskState);
    }

    private List<ScheduledTask> applyActiveFilters(List<ScheduledTask> tasks) {
        String searchText = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = (String) statusFilterCombo.getSelectedItem();
        String selectedCategory = (String) categoryFilterCombo.getSelectedItem();
        int maxPriority = ((Number) maxPrioritySpinner.getValue()).intValue();
        long now = lastSnapshot == null ? System.currentTimeMillis() : lastSnapshot.generatedAt();
        SchedulerPolicySettings settings = lastSnapshot == null ? SchedulerPolicySettings.defaults() : lastSnapshot.policySettings();

        List<ScheduledTask> filtered = new ArrayList<>();
        for (ScheduledTask task : tasks) {
            if (task.isTerminal()) {
                continue;
            }
            if (selectedStatus != null && !"All".equals(selectedStatus) && !task.getStatus().name().equals(selectedStatus)) {
                continue;
            }
            if (selectedCategory != null
                    && !"All".equals(selectedCategory)
                    && !selectedCategory.equalsIgnoreCase(task.getCategory())) {
                continue;
            }
            if (task.getPriority() > maxPriority) {
                continue;
            }
            if (atRiskOnlyCheckBox.isSelected() && !task.isAtRisk(settings, now)) {
                continue;
            }
            if (!searchText.isBlank()) {
                String haystack = (
                        task.getName() + " "
                                + task.getCategory() + " "
                                + task.getTagsDisplay() + " "
                                + task.getNotes() + " "
                                + task.getId() + " "
                                + task.getSeriesId() + " "
                                + task.getRetrySummary()
                ).toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchText)) {
                    continue;
                }
            }
            filtered.add(task);
        }

        return filtered;
    }

    private List<ScheduledTask> applyHistoryFilters(List<ScheduledTask> tasks) {
        String searchText = historySearchField.getText() == null
                ? ""
                : historySearchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = (String) historyStatusFilterCombo.getSelectedItem();
        String selectedCategory = (String) historyCategoryFilterCombo.getSelectedItem();

        List<ScheduledTask> filtered = new ArrayList<>();
        for (ScheduledTask task : tasks) {
            if (!task.isTerminal()) {
                continue;
            }
            if (selectedStatus != null && !"All".equals(selectedStatus) && !task.getStatus().name().equals(selectedStatus)) {
                continue;
            }
            if (selectedCategory != null
                    && !"All".equals(selectedCategory)
                    && !selectedCategory.equalsIgnoreCase(task.getCategory())) {
                continue;
            }
            if (historySpecialOnlyCheckBox.isSelected()
                    && task.getRetryAttempt() == 0
                    && task.getRecurrenceLimit() <= 1) {
                continue;
            }
            if (!searchText.isBlank()) {
                String haystack = (
                        task.getName() + " "
                                + task.getCategory() + " "
                                + task.getTagsDisplay() + " "
                                + task.getNotes() + " "
                                + task.getId() + " "
                                + task.getSeriesId() + " "
                                + task.getRetrySummary()
                ).toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchText)) {
                    continue;
                }
            }
            filtered.add(task);
        }

        filtered.sort((left, right) -> Long.compare(right.getFinishedAt(), left.getFinishedAt()));
        return filtered;
    }

    private void updateCategoryFilterOptions(List<ScheduledTask> tasks) {
        String previousSelection = (String) categoryFilterCombo.getSelectedItem();
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ScheduledTask task : tasks) {
            if (!task.isTerminal() && !task.getCategory().isBlank()) {
                categories.add(task.getCategory());
            }
        }

        categoryFilterCombo.removeAllItems();
        categoryFilterCombo.addItem("All");
        for (String category : categories) {
            categoryFilterCombo.addItem(category);
        }

        if (previousSelection != null) {
            boolean exists = "All".equals(previousSelection) || categories.contains(previousSelection);
            categoryFilterCombo.setSelectedItem(exists ? previousSelection : "All");
        }
    }

    private void updateHistoryCategoryFilterOptions(List<ScheduledTask> tasks) {
        String previousSelection = (String) historyCategoryFilterCombo.getSelectedItem();
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ScheduledTask task : tasks) {
            if (task.isTerminal() && !task.getCategory().isBlank()) {
                categories.add(task.getCategory());
            }
        }

        historyCategoryFilterCombo.removeAllItems();
        historyCategoryFilterCombo.addItem("All");
        for (String category : categories) {
            historyCategoryFilterCombo.addItem(category);
        }

        if (previousSelection != null) {
            boolean exists = "All".equals(previousSelection) || categories.contains(previousSelection);
            historyCategoryFilterCombo.setSelectedItem(exists ? previousSelection : "All");
        }
    }

    private void updateLiveDetails() {
        ScheduledTask selectedTask = null;
        List<ScheduledTask> selectedTasks = getSelectedTasksFromActiveTable();
        if (!selectedTasks.isEmpty()) {
            selectedTask = selectedTasks.get(0);
        }

        if (lastSnapshot == null) {
            liveDetailsPanel.showTask(selectedTask, SchedulerPolicySettings.defaults(), System.currentTimeMillis());
            return;
        }
        liveDetailsPanel.showTask(selectedTask, lastSnapshot.policySettings(), lastSnapshot.generatedAt());
    }

    private void updateHistoryDetails() {
        ScheduledTask selectedTask = getSelectedHistoryTask();
        if (lastSnapshot == null) {
            historyDetailsPanel.showTask(selectedTask, SchedulerPolicySettings.defaults(), System.currentTimeMillis());
            return;
        }
        historyDetailsPanel.showTask(selectedTask, lastSnapshot.policySettings(), lastSnapshot.generatedAt());
    }

    private void updateLogs(List<String> logs) {
        if (logs.size() < lastRenderedLogCount) {
            logArea.setText("");
            lastRenderedLogCount = 0;
        }

        if (logs.size() == lastRenderedLogCount) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int index = lastRenderedLogCount; index < logs.size(); index++) {
            builder.append(logs.get(index)).append(System.lineSeparator());
        }

        logArea.append(builder.toString());
        logArea.setCaretPosition(logArea.getDocument().getLength());
        lastRenderedLogCount = logs.size();
    }

    private void clearActiveFilters() {
        searchField.setText("");
        statusFilterCombo.setSelectedItem("All");
        categoryFilterCombo.setSelectedItem("All");
        maxPrioritySpinner.setValue(10);
        atRiskOnlyCheckBox.setSelected(false);
    }

    private void clearHistoryFilters() {
        historySearchField.setText("");
        historyStatusFilterCombo.setSelectedItem("All");
        historyCategoryFilterCombo.setSelectedItem("All");
        historySpecialOnlyCheckBox.setSelected(false);
    }

    private void focusTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }

        ScheduledTask matchingTask = null;
        if (lastSnapshot != null) {
            for (ScheduledTask task : lastSnapshot.tasks()) {
                if (taskId.equals(task.getId())) {
                    matchingTask = task;
                    break;
                }
            }
        }

        if (matchingTask != null && matchingTask.isTerminal()) {
            clearHistoryFilters();
            pendingHistorySelectionTaskId = taskId;
            workspaceTabs.setSelectedComponent(historyTab);
        } else {
            clearActiveFilters();
            pendingActiveSelectionTaskId = taskId;
            workspaceTabs.setSelectedComponent(overviewTab);
        }
        refreshUi();
    }

    private void shutdownAndClose() {
        refreshTimer.stop();
        engine.shutdownGracefully();
        dispose();
    }

    private enum ActionType {
        PAUSE,
        RESUME,
        CANCEL,
        EDIT,
        CLONE
    }

    private class ActiveTaskTextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            ScheduledTask task = tableModel.getTaskAt(modelRow);
            long now = lastSnapshot == null ? System.currentTimeMillis() : lastSnapshot.generatedAt();
            SchedulerPolicySettings settings = lastSnapshot == null ? SchedulerPolicySettings.defaults() : lastSnapshot.policySettings();

            if (column == 5 && value instanceof Long epochMs) {
                setText(TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs)));
            } else if (column == 6 && value instanceof Long epochMs) {
                setText(TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs)));
            } else {
                setText(value == null ? "" : String.valueOf(value));
            }

            if (!isSelected && task != null) {
                setBackground(colorForActiveTask(task, settings, now));
                setForeground(Color.BLACK);
            } else if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }
            return this;
        }
    }

    private class HistoryTaskTextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            ScheduledTask task = historyTableModel.getTaskAt(modelRow);

            if (column == 0 && value instanceof Long epochMs && epochMs > 0L) {
                setText(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs)));
            } else {
                setText(value == null ? "" : String.valueOf(value));
            }

            if (!isSelected && task != null) {
                setBackground(colorForHistoryTask(task));
                setForeground(Color.BLACK);
            } else if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }
            return this;
        }
    }

    private class ProgressBarRenderer extends javax.swing.JProgressBar implements TableCellRenderer {
        private ProgressBarRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            int progress = value instanceof Number number ? number.intValue() : 0;
            setValue(progress);
            setString(progress + "%");

            int modelRow = table.convertRowIndexToModel(row);
            ScheduledTask task = tableModel.getTaskAt(modelRow);
            long now = lastSnapshot == null ? System.currentTimeMillis() : lastSnapshot.generatedAt();
            SchedulerPolicySettings settings = lastSnapshot == null ? SchedulerPolicySettings.defaults() : lastSnapshot.policySettings();

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if (task != null) {
                setBackground(colorForActiveTask(task, settings, now));
                setForeground(Color.BLACK);
            }
            return this;
        }
    }

    private Color colorForActiveTask(ScheduledTask task, SchedulerPolicySettings settings, long now) {
        if (task == null) {
            return Color.WHITE;
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            return new Color(255, 243, 205);
        }
        if (task.getStatus() == TaskStatus.PAUSED) {
            return new Color(255, 249, 196);
        }
        if (task.isAtRisk(settings, now)) {
            return new Color(255, 235, 238);
        }
        if (task.isDeferred(now)) {
            return new Color(237, 247, 255);
        }
        return Color.WHITE;
    }

    private Color colorForHistoryTask(ScheduledTask task) {
        if (task == null) {
            return Color.WHITE;
        }
        return switch (task.getStatus()) {
            case COMPLETED -> new Color(232, 245, 233);
            case MISSED -> new Color(255, 235, 238);
            case CANCELED -> new Color(245, 245, 245);
            default -> Color.WHITE;
        };
    }
}
