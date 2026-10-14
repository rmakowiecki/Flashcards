# User Flow — Progress

NYI — flows designed from ADRs and design docs. See [docs/design/planned/progress-dashboard.md](../design/planned/progress-dashboard.md), [docs/design/planned/launcher-widget.md](../design/planned/launcher-widget.md), [docs/design/session-submission.md](../design/session-submission.md).

## Entry points & screen

Widget tap on the device home screen opens the app and navigates directly to the Progress tab. If the app cold-starts, auth check runs first (see [main.md](main.md)).

```mermaid
flowchart TD

    %% Legend: (Screen)  [/Action/]  {Decision}  ([Entry/Exit])  {{NYI}}

    %% ── Entry points ──────────────────────────────────────────────
    WidgetTap([Launcher widget tap\ncompact or large]) --> ProgressScreen
    ProgressTab([Progress tab]) --> ProgressScreen

    %% ── Progress Screen ───────────────────────────────────────────
    ProgressScreen(PROGRESS SCREEN\nNYI)

    %% Section 1 — Level card
    ProgressScreen --> LevelCard[Level card\nLevel number · XP progress bar\nno action]

    %% Section 2 — Daily goal
    ProgressScreen --> TapGoalRing[/Tap daily goal ring\ncircular progress: today / goal min/]
    TapGoalRing --> GoalPicker(GOAL PICKER\nstepper dialog)
    GoalPicker -->|set new goal| ProgressScreen

    %% Section 3 — Streak
    ProgressScreen --> StreakRow[Streak row\ncurrent streak · best streak\nno action]

    %% Section 4 — Weekly time
    ProgressScreen --> WeeklyTime[Weekly study time\nlast 7 days · no action]

    %% Section 5 — Stats row
    ProgressScreen --> StatsRow[Stats row\ntotal time · sessions completed · cards studied · cards mastered\nno action]

    %% Section 6 — History chart
    ProgressScreen --> HistoryChart[History chart\nstacked bar by category · one bar per day]
    HistoryChart --> ChartToggle[/Toggle: 7 days ↔ 30 days/]
    ChartToggle --> HistoryChart

    %% Section 7 — Per-category breakdown
    ProgressScreen --> PerCategory[Per-category breakdown\ncategory · color swatch · minutes in the chart window\nordered by time studied desc\nno action in MVP]
```

## Widget sizes

| Size | Content |
|---|---|
| Compact (1 row × 4 columns) | Streak (flame + day count) · Daily goal ring with today's minutes |
| Large (2 rows × 4 columns) | Level number + XP progress bar · Streak · Today's goal · Last 7 days minutes |

The widget reads the same figures as the Progress tab and refreshes on events, not on a timer. See [launcher-widget.md](../design/planned/launcher-widget.md).

## Data sources

Every figure is read from a server-written document and includes Pending Sessions. See [progress-dashboard.md](../design/planned/progress-dashboard.md#where-the-figures-come-from).

| Stat | Source |
|---|---|
| Level, XP, streak, best streak, today's minutes | Scoring state |
| Cards studied, cards mastered | Progress summary |
| Total time, sessions completed, weekly minutes, history chart (7/30 days), per-category breakdown | Activity aggregate |
