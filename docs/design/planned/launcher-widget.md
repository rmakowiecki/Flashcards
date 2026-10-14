# Launcher Widget

A home screen widget that shows the User's study progress at a glance without opening the app. It is purely informational, with no button that starts a session. A tap anywhere on it opens the Progress tab. It shows a subset of the figures on the Progress tab ([progress-dashboard.md](progress-dashboard.md)).

## Sizes

The widget comes in two fixed layouts. A User who resizes it gets the nearest layout, so there is no third one to design.

- **Compact**, one row tall and four columns wide:
  - the current Streak with a flame icon;
  - the Daily Goal ring, today's minutes against the goal, with a label giving both.
- **Large**, two rows tall and four columns wide:
  - the Level number, with a bar showing the XP earned into the current Level against the XP that completes it;
  - the current Streak with a flame icon;
  - today's minutes against the Daily Goal;
  - the minutes studied in the last 7 days.

## Where the figures come from

The widget shows the same figures as the Progress tab, read the same way. At each refresh it reads the same repositories the Progress tab reads, and keeps no copy of its own. The sources are listed in [progress-dashboard.md](progress-dashboard.md#where-the-figures-come-from) and the reasoning is in [ADR-0015](../../adr/0015-progress-figures-read-from-server-documents.md).

- Pending Sessions are included as soon as they are queued, so a session studied offline shows at once ([progress-dashboard.md](progress-dashboard.md#pending-sessions)).
- Stale values are derived against the local date by the same rule as the Progress tab: the current Streak reads zero once the last study day is earlier than yesterday, and today's minutes read zero unless the last study day is today ([progress-dashboard.md](progress-dashboard.md#stale-values)). A widget that sits on the home screen for days therefore never shows yesterday's minutes as today's.
- The 7-day figure is a rolling window that ends today, not a calendar week ([progress-dashboard.md](progress-dashboard.md#windows)).
- The Daily Goal is the device preference ([settings.md](../settings.md)).

The 7-day figure comes from the activity aggregate, which is added to session submission with the Progress tab. The widget therefore ships after or together with the Progress tab.

## States

- **Not signed in.** The device has not finished onboarding, no User is signed in, or the session is a Guest, which counts as not signed in ([navigation.md](../navigation.md#launch)). The widget shows a prompt to sign in, and tapping it follows the normal launch route.
- **Figures not available yet.** A User is signed in, but the device has no copy of their scoring state and cannot reach the server, or the read failed. The widget shows a neutral state with no numbers. Unknown is never shown as zero, and never as Level 1.
- **A new User.** A User is signed in and has no sessions. The widget shows real zeros and an empty ring.
- **Figures.** The normal state.

Signing out, or Account Deletion, moves every widget on the device to the not-signed-in state at once. A widget never shows the figures of a User who is no longer signed in.

## When the widget refreshes

The widget refreshes on events and never on a timer. There is no polling and no exact alarm. It refreshes when:

- a session is queued;
- a session is delivered, and the server's numbers replace the projection;
- the Daily Goal is edited, in Settings or on the Progress tab;
- a User signs in or out, or Account Deletion completes;
- the local date changes, which resets today's minutes and can end the Streak;
- the device's timezone or clock changes, which moves what counts as today.

Between those events the widget shows what its last refresh computed. The launcher also draws the widget when it is added and after a reboot, which needs no trigger of its own.

## Tapping

A tap anywhere on either layout opens the Progress tab, resolved the way a launcher shortcut is ([launcher-shortcuts.md](../launcher-shortcuts.md#opening-a-shortcut)):

- If the device has not finished onboarding or no User is signed in, the launch goes through onboarding or Login as usual and the Progress target is dropped, not applied afterwards.
- If the app is not running and a User is signed in, Main opens with the Progress tab selected.
- If the app is running, the Progress tab is selected.
- If a Study Session, Preview or Summary is on top of Main, the tap only brings the app to the front and leaves the screen as it is, so nothing the User is doing is discarded.

Back from the Progress tab behaves as it does after tapping the tab by hand ([navigation.md](../navigation.md#tab-shell)).

## Not included

- A button that starts a session.
- The history chart and the per-Category breakdown.
- Sessions Completed, Cards Studied and Cards Mastered.

The widget surface is too small for them, and the Progress tab shows them.

## Related

- [progress-dashboard.md](progress-dashboard.md): the figures, their sources, Pending Sessions, stale values and windows.
- [launcher-shortcuts.md](../launcher-shortcuts.md): how a tap on a shortcut is resolved at launch.
- [navigation.md](../navigation.md): launch routing and the tab shell.
- [settings.md](../settings.md): where the Daily Goal is edited today.
- [ADR-0015](../../adr/0015-progress-figures-read-from-server-documents.md): why the figures are read from server documents with no local statistics cache.
- [ADR-0055](../../adr/0055-pending-sessions-and-local-progress-projection.md): Pending Sessions count at once.
- [docs/user-flow/progress.md](../../user-flow/progress.md): the navigation diagram.
