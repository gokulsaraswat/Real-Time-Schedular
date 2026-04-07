# Real-Time Scheduler — Phase 4

A Java 17 desktop scheduler with a Swing dashboard for simulating **priority-based preemptive task execution** on a **single local machine**.

Phase 4 builds on the smarter Phase 3 scheduler core and upgrades the **desktop workflow layer** so the app is easier to operate day to day on a local setup:

- separate **active workspace** and **history** views
- richer **local notification center**
- stronger **filters, sorting, and bulk actions**
- better **task inspection** for live and completed tasks
- cleaner desktop workflow for a single-machine operator

---

## What changed in Phase 4

### 1) Active workspace vs history
The dashboard is now split into two clearer views:

- **Active Workspace**
  - queued, deferred, running, and paused tasks
  - active-only filters
  - bulk pause / resume / cancel
  - selected live task details
- **History**
  - completed, missed, and canceled tasks
  - searchable history
  - outcome/category filters
  - history details for completed runs

This keeps operational work separate from historical inspection.

### 2) Notification center
Phase 4 adds an in-app **notification center** for local monitoring.

It now surfaces alerts for:

- task completion
- missed deadlines
- task cancelation
- pause / resume changes
- at-risk tasks
- automatic retry creation

The notification panel supports:

- unread count
- mark all read
- clear alerts
- optional beep on error alerts
- double-click an alert to jump to the related task

### 3) Better local workflow controls
The desktop app now supports stronger operator workflows:

- **multi-select** active tasks
- bulk **pause**
- bulk **resume**
- bulk **cancel**
- quick **Refresh**
- **Open Data Folder** for local state inspection

Single-task actions still support:

- edit selected
- retry / clone selected

### 4) Better filters and sorting
The active view now adds:

- free-text search
- status filter
- category filter
- **max priority** filter
- **at-risk only** toggle

The history view now adds:

- free-text search
- outcome filter
- category filter
- **recurring / retry only** toggle

Both tables support column sorting directly from the Swing table headers.

### 5) Clearer visual cues
Phase 4 improves visual scanning on a local desktop:

- live tasks are color-tinted by state
- at-risk tasks are highlighted
- history entries are color-coded by outcome
- progress is shown with an in-table progress bar
- task details now show start/finish timestamps and richer execution context

### 6) Operational metrics grouped for workflow
The metrics panel now distinguishes:

- total tasks
- active workspace count
- history entry count
- queue/deferred/running/paused counts
- completed/missed/canceled counts
- at-risk tasks
- retries
- average wait time
- on-time rate
- total preemptions

---

## Dashboard overview

The Swing dashboard now includes:

- **Current Task**
- **Scheduler Policy**
- **Local Scheduler Health**
- **Alerts**
- **Execution Logs**
- **Execution Timeline**
- **Active Workspace**
- **History**
- **Selected Active Task**
- **Selected History Entry**
- **Add New Task**
- **Bulk actions**
- **Open Data Folder**
- **Export Logs**

---

## Scheduling behavior

### Priority rule
- **1 = highest priority**
- **10 = lowest priority**

### Policy modes
- **Priority First**  
  Uses priority as the main dispatch rule, while still allowing aging to improve fairness.

- **Deadline First**  
  Prefers the task with the smallest slack / earliest effective deadline pressure.

- **Adaptive**  
  Blends fairness and urgency by considering:
  - at-risk deadline status
  - effective priority after aging
  - slack

### Automatic retry behavior
- retries happen only for **MISSED** tasks
- retry attempts use the configured **backoff**
- retry attempts are tracked separately from the original task
- retry policy can be disabled globally from the dashboard

---

## Requirements

- Java 17 or newer
- VS Code / IntelliJ / Eclipse, or command line

---

## How to run

From the project root:

```bash
javac scheduler/*.java
java scheduler.RealTimeScheduler
```

---

## Local data storage

Task state is saved locally to:

- `~/.real-time-scheduler/tasks.ser`
- `~/.real-time-scheduler/scheduler.log`
- `~/.real-time-scheduler/scheduler-settings.ser`

---

## Phase 4 focus

This phase improves the **desktop workflow and history UX** for local operation.

The next patch can build on this base with:

- exportable reports and richer summaries
- settings / local quality-of-life polish
- backup / restore helpers
- final cleanup and packaging improvements
