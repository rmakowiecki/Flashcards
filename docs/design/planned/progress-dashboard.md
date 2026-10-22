# Progress Dashboard

The Progress tab: one screen that shows the User how much they have studied and how they are doing. It combines the Level card, the Daily Goal, the Streak, a few lifetime figures, a history chart of minutes studied per day by Category, and a per-Category breakdown. Every figure is read from server-written documents and includes Pending Sessions, so the screen works offline and needs no local statistics cache of its own.

## Navigation

The tab bar gains a Progress tab between Study and Settings, which follows the natural loop: discover, study, review progress, configure. Debug builds keep their extra hub tab as described in [navigation.md](../navigation.md). Like every other tab, Progress is its own small graph with one root screen.

## Where the figures come from

Nothing is cached locally for this screen, and nothing is recomputed from raw session history on each visit ([ADR-0015](../../adr/0015-progress-figures-read-from-server-documents.md)).

- **Level, XP, Streak, Best Streak and the day's minutes** come from the User's scoring state, the same source as Home's Level card ([xp-leveling-system.md](../xp-leveling-system.md), [session-submission.md](../session-submission.md#scoring-state)).
- **Cards Studied and Cards Mastered** come from the progress summary that also feeds the progress rings ([browse.md](../browse.md#progress-rings)).
- **Total study time, Sessions Completed, the history chart and the per-Category breakdown** come from a server-maintained activity aggregate. Session submission updates it in the same transaction that records the session, so the screen reads a small, fixed number of documents however long the User's history is. A User who reinstalls the app or signs in on a new device gets every figure back without replaying any session. The aggregate's shape is designed with its entry in [firestore-schema.md](../firestore-schema.md) when the screen is built. It must hold lifetime totals and minutes per study day and Category.
- **The Daily Goal** is a device preference, not server state ([settings.md](../settings.md)).

### Pending Sessions

Every figure on the screen includes Pending Sessions as soon as they are queued, so a session that has not been delivered yet is never missing from the Streak, the goal ring, the totals or the chart ([ADR-0055](../../adr/0055-pending-sessions-and-local-progress-projection.md)). The activity aggregate therefore needs a pure rule shared by the server and the device, with shared test cases run against both implementations, as the existing scoring rules have.

### Stale values

The Streak, Best Streak and today's minutes follow the same rules about today as on Home ([home.md](../home.md#streak-and-daily-goal)).

## Layout

From top to bottom:

1. **Level card.** The same shared card as Home's, with the same live updates, loading skeleton and pour. It is not tappable ([home.md](../home.md#level-card)).
2. **Streak and Daily Goal.** The same row of two cards as on Home ([home.md](../home.md#streak-and-daily-goal)).
3. **Weekly study time.** The minutes studied in the last 7 days, including today, as a single stat line.
4. **Summary figures.** Four figures in a row:
   - total study time, all-time;
   - **Sessions Completed**, which counts deck-end sessions only ([session-submission.md](../session-submission.md#abandoned-sessions));
   - Cards Studied, from both Study Modes, which never goes down;
   - Cards Mastered, the Persistent Mastery set held now, which goes down on de-mastery rather than counting every card ever mastered.
5. **History chart.** A stacked bar chart with one bar per calendar day. A bar's height is the minutes studied that day, and each segment is one Category in that Category's own color ([category-icon-color.md](../category-icon-color.md)). A toggle above the chart switches between the last 7 days, which is the default, and the last 30 days. Bars are not interactive in the first version.
6. **Per-Category breakdown.** A section directly below the chart that doubles as its legend. It has one row per Category studied in the chart's selected window, showing a color swatch, the name and the minutes, ordered by minutes studied, most first. Because it follows the toggle, it always describes the same period as the chart. Rows are not tappable in the first version.

## Windows

The weekly stat, the chart and the breakdown all use rolling windows that end today. There is no calendar week and no locale-dependent first day of the week. Days are the study days defined in [session-submission.md](../session-submission.md#study-day), so a session crossing midnight counts toward the day it began.

## Loading, empty and failed states

- **Unknown is never zero.** Each section shows a skeleton until its own data arrives. Sections load independently and never wait for one another.
- **A failed section** shows an error with Retry for that section only and leaves the others as they are.
- **A User with no sessions yet** sees real zeros in the stat figures, and the chart shows an empty-state message instead of empty bars.
- **A Category that has left the taxonomy** keeps the name stored with the sessions and takes the standard fallback color. Segments are never capped or merged into an "other" group.

## What counts

Study time, Sessions Completed and the Streak include sessions made up only of Private Flashcards, because those sessions are recorded ([private-flashcards.md](private-flashcards.md#in-a-study-session)). Cards Studied and Cards Mastered never move for a Private Flashcard, as it has no Card Progress ([ADR-0016](../../adr/0016-card-progress-model.md)).

## Related

- [home.md](../home.md): the Level card this screen shares.
- [session-submission.md](../session-submission.md): the scoring state, the study day and the Streak and Daily Goal rules.
- [settings.md](../settings.md): where the Daily Goal is stored and edited today.
- [browse.md](../browse.md#progress-rings): the progress summary behind Cards Studied and Cards Mastered.
- [category-icon-color.md](../category-icon-color.md): Category colors and fallbacks.
- [launcher-widget.md](launcher-widget.md): a widget that shows a subset of these figures and opens this tab.
- [ADR-0015](../../adr/0015-progress-figures-read-from-server-documents.md): why the figures are not cached locally.
- [ADR-0055](../../adr/0055-pending-sessions-and-local-progress-projection.md): Pending Sessions count at once.
- [docs/user-flow/progress.md](../../user-flow/progress.md): the navigation diagram.
