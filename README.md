# 🕒 Real-Time Scheduler

A Java-based real-time task scheduling system with a **Swing-based GUI** that supports:
- **Deadline-aware task execution**
- **Dynamic task addition**
- **Live logs**
- **Visualized Gantt chart**
- **Performance metrics**
- **Exportable execution logs**

It demonstrates the practical implementation of **preemptive scheduling** based on task priority using **multi-threading** and **PriorityBlockingQueue**.
 
Here the goal was to simulate an OS-like preemptive scheduler that can handle multiple tasks with different priorities and execute the highest-priority tasks first useful for systems like:
- Operating systems (process scheduling)
- Real-time embedded systems (like drones, robots)
- Server-side task orchestration
- IoT devices

---

## 📌 Table of Contents

- [📸 Demo](#-demo)
- [📋 Features](#-features)
- [🧠 Architecture](#-architecture)
- [🧰 Tech Stack](#-tech-stack)
- [⚙️ Setup Instructions](#️-setup-instructions)
- [🚀 How to Use](#-how-to-use)
- [⚙ How It Works](#-how-it-works)
- [📊 GUI Layout](#-gui-layout)
- [🧵 Threading Model](#-threading-model)
- [🛑 Error Handling](#-error-handling)
- [📦 Class Overview](#-class-overview)
- [✅ Testing](#-testing)
- [📤 Export Logs](#-export-logs)
- [🛠️ Future Enhancements](#️-future-enhancements)
- [📄 License](#-license)

---

## 📋 Features

- ✅ **Multi-threaded real-time task execution**
- 🛑 **Deadline & interruption-aware scheduler**
- ✍️ **User can dynamically add new tasks**
- 📈 **Gantt chart visualization**
- 📚 **Log Viewer and Exporter**
- 📉 **Performance Metrics Dashboard**
- 🔃 **Live updates of task statuses (Completed / Interrupted / Missed)**

### ⚙️ Priority-Based Preemptive Scheduling
- Each task has a priority (e.g., 1 = high, 5 = low). Tasks with higher priority are executed first.

### 🔁 Multi-threaded Execution
- Utilizes multi-threading to process concurrent tasks and simulate and handle up to **50 concurrent tasks per second**, improving CPU utilization and responsiveness.

### 🔐 PriorityBlockingQueue Integration
- Tasks are maintained in a thread-safe priority queue, which supports dynamic reordering and blocking for scheduling.

### 📆 Deadline-Aware Execution
- Ensures **100% deadline adherence** for scheduled tasks, i.e. real-time constraints for high-priority tasks.

### 🔄 Preemptive Simulation
- If a higher-priority task enters the queue while a lower-priority task is running, the running task is paused or marked as incomplete.

---

## 🧠 Architecture

```bash
RealTimeScheduler/
│
├── scheduler/
│   ├── RealTimeScheduler.java      # Main driver class to run scheduler
│   ├── ScheduledTask.java          # Task entity with priority, deadline, etc.
│   ├── TaskExecutor.java           # Executes and manages task threads
│   ├── LoggerUtil.java             # Thread-safe logging utility
│   └── utils/                      # Any additional utility classes
│
└── README.md
```

- `RealTimeScheduler`: Core scheduler, prioritizes and executes tasks using a `PriorityBlockingQueue`.
- `ScheduledTask`: Represents a task with priority, duration, and deadline.
- `SchedulerDashboard`: Swing GUI that manages:
  - Gantt chart visualization (`TaskChartPanel`)
  - Performance metrics (`PerformanceMetricsPanel`)
  - Log viewer and export
  - Task status lists
- `LoggerUtil`: Thread-safe logging utility with file export.

![Architecture Diagram](demo.png)
<!-- ![Log](demolog.png) -->

---

## 🧰 Tech Stack

| Technology | Usage |
|-----------|--------|
| Java 17+ | Core logic and multi-threading |
| Swing     | Graphical User Interface (GUI) |
| Collections & Concurrency | PriorityBlockingQueue, Synchronized Lists |
| Java IO   | Log Exporting |

---

## ⚙️ Setup Instructions

### Prerequisites
- Java 17 or above
- IDE (IntelliJ / Eclipse / VSCode) or command-line

### Steps

1. Clone or Download the Repository:
```bash
git clone https://github.com/yourusername/real-time-scheduler.git
```

2. Compile:
```bash
javac scheduler/*.java
```

3. Run:
```bash
java scheduler.RealTimeScheduler
```

---

## 🚀 How to Use

1. On launch, the dashboard appears with:
   - Execution Logs (center)
   - Gantt Chart (top)
   - Performance Metrics (left)
   - Task Statuses (right)
   - Add Task Form (bottom)

2. Add a task by filling:
   - Name
   - Priority
   - Duration (ms)
   - Deadline (timestamp in ms)

3. Use:
   - 📤 **Export Logs** to save execution history
   - 📚 **View Logs** to inspect runtime logs

---

## ⚙ How It Works

1. **Task Submission**:
   - A task is created with a name, priority, duration, and deadline.
   - Submitted to the `PriorityBlockingQueue`.

2. **Scheduling**:
   - Tasks are polled from the queue.
   - If a task is executing and a higher-priority task enters, the current task may be preempted.

3. **Execution**:
   - Executed in a thread using `ExecutorService`.
   - Real-time checks for deadline miss or completion.

4. **Logging**:
   - All operations (start, end, interruptions) are logged via `LoggerUtil`.
   - All execution logs are saved using `LoggerUtil`
   - Click on `Export Logs` button to save logs to a file `scheduler_logs.txt` 
   - Click on `View Logs` to inspect real-time execution in a scrollable window

---

## 📊 GUI Layout

```
+---------------------------------------------------+
| Task Progress Bar                                 |
+--------------------+------------------------------+
| Performance Metrics|                              |
| - Total Tasks      |      Visual Gantt Chart      |
| - Completed        |                              |
| - Interrupted      |------------------------------|
| - Missed           |      Task Statuses           |
+--------------------|(Completed|Missed|Interrupted)|
| Execution Logs     |                              |
+---------------------------------------------------+
| Add Task Form + Export Logs + View Logs Buttons   |
+---------------------------------------------------+
```

---

## 🧵 Threading Model
- Uses a `FixedThreadPool` via `ExecutorService` to manage concurrency.
- `PriorityBlockingQueue` ensures thread-safe scheduling and ordering.
- Threads simulate preemption by checking priorities dynamically and gracefully exiting interrupted tasks.

---

## 🛑 Error Handling
- Validates task input in GUI (e.g., negative duration not allowed).
- Handles edge cases like:
  - Missed deadlines
  - Interrupted tasks
  - Queue starvation
  - Long-running tasks
- Errors are logged with timestamps in `LoggerUtil`.

---

## 📦 Class Overview

| Class Name             | Responsibility                          |
|------------------------|------------------------------------------|
| `ScheduledTask`        | Represents a task with name, priority, duration, deadline |
| `RealTimeScheduler`    | Main orchestrator; manages queue, execution, preemption |
| `TaskExecutor`         | Executes tasks with interruption checks  |
| `SchedulerDashboard`   | GUI layer (log viewer, Gantt, metrics)   |
| `LoggerUtil`           | Thread-safe logger with file export      |
| `TaskChartPanel`       | Custom panel to draw real-time Gantt     |

---

## ✅ Testing

Tested with:
- 0 to 1000 dynamic task insertions
- Tasks with same deadlines but different priorities
- Preemption of long-running low-priority tasks
- Log export and deadline tracking accuracy

---
## 🔍 Comparison with Other Scheduling Systems

| Feature                  | Real-Time Scheduler | OS-Level Scheduler | Cron Jobs     |
|--------------------------|---------------------|---------------------|---------------|
| Preemptive Support       | ✅ Yes              | ✅ Yes              | ❌ No         |
| GUI Dashboard            | ✅ Yes              | ❌ No               | ❌ No         |
| Real-Time Visualization  | ✅ Yes              | ❌ No               | ❌ No         |
| Custom Task Addition     | ✅ Yes              | ⚠️ Limited         | ✅ Yes        |
| Dynamic Priority Control | ✅ Yes              | ✅ Yes              | ❌ No         |
| Deadline Awareness       | ✅ Yes              | ⚠️ Depends         | ❌ No         |
| Suitable for Embedded/IoT| ✅ Yes              | ⚠️ Depends         | ❌ No         |
| Observability Ready      | ✅ Planned          | ⚠️ External Tools  | ⚠️ Minimal    |
| Exportable Logs          | ✅ Yes              | ⚠️ Limited         | ⚠️ Manual     |

---

## 📤 Export Logs
- All operations (start, end, interruption, deadline miss) are logged.
- Click `Export Logs` to save to `scheduler_logs.txt`
- Click `View Logs` to see real-time logs in scrollable UI

---

## 🛠️ Future Enhancements
- [ ] Save/load task queue from file
- [ ] User preference settings
- [ ] Historical performance charting
- [ ] Networked version for remote monitoring
- [ ] Sound/visual alerts for missed deadlines
- [ ] Integrate into a Microservice for task orchestration
- [ ] Add Observability in Microservice: 
  - Prometheus + Grafana: For performance metrics
  - Zipkin / Jaeger: For distributed tracing
  - Micrometer: With Spring Boot for exposing metrics
  - Keycloak or JWT for secure scheduling endpoints

---

> “Scheduling isn't just about running things on time, it's about running the right things at the right time.”

---

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).

Made with 💻 by Gokul Saraswat
