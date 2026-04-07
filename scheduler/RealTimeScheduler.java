
package scheduler;

import javax.swing.SwingUtilities;

public class RealTimeScheduler {
    private static final int MAX_TASKS = 5000;

    public static void main(String[] args) {
        SchedulerStateStore stateStore = new SchedulerStateStore();
        LoggerUtil.init(stateStore.getDataDirectory());

        SchedulerEngine engine = new SchedulerEngine(stateStore, MAX_TASKS);
        Runtime.getRuntime().addShutdownHook(new Thread(engine::shutdownGracefully, "scheduler-shutdown"));
        engine.start();

        SwingUtilities.invokeLater(() -> {
            SchedulerDashboard dashboard = new SchedulerDashboard(engine, stateStore.getDataDirectory());
            dashboard.setVisible(true);
        });
    }
}
