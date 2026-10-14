# Destinations navigate only from their own back-stack entry

> Status: accepted

## Context

Navigation Compose keeps an outgoing screen tappable through its exit transition, and
`NavController.navigate` and `popBackStack` never dedupe. A second tap in that window pushed a second
screen or popped one too many. Only a few ViewModels had in-flight flags.

## Decision

- Every destination, root and tab graphs alike, is declared with `guardedNavComposable<T>(navController)`
  (`app/.../ui/navigation/GuardedNavComposable.kt`), not `composable<T>`. Its content gets an
  `EntryNavigator` bound to its own back-stack entry.
- `EntryNavigator` drops every call unless that entry is `currentBackStackEntry`, compared by
  `NavBackStackEntry.id` so a process-death restore still matches. A screen navigates again once back
  on top.
- The check runs once per call, so a callback makes at most one navigator call. A multi-call sequence
  is its own method: `navigateStacked(base, baseBuilder, route)`, used by Splash to open a shortcut's
  Details screen above Main.
- The bottom bar's tab switch is the one raw controller call: it has no entry, and `launchSingleTop`
  makes a repeated tap harmless.
- `ArchitectureKonsistTest` enforces it: only `GuardedNavComposable.kt` imports Navigation Compose's
  `composable`; production code calls no `navigate`, `popBackStack` or `navigateUp` on a
  `NavController` except the tab switch; a callback makes at most one `navigator.` call.

## Alternatives considered

- **Lifecycle guard** (only while `RESUMED`): an entering screen stays below `RESUMED` until its enter
  transition ends, so it would drop legitimate taps.
- **Click debounce**: a time window is a guess, too short on slow devices, too long for a deliberate tap.
- **Per-ViewModel in-flight flags**: one flag and reset per screen; a missed reset leaves the screen
  dead. Kept only for non-navigation work, like Preview's microphone request.
- **Input-blocking overlay on exiting screens**: would also stop the delayed event below, but that harms
  only Preview's Start, so it isn't worth a layer on every transition.

## Consequences

- No direct callback can push a screen twice or pop one too many.
- **A ViewModel's navigation event can outlive the guard.** `observeAsEvents` collects under
  `repeatOnLifecycle(STARTED)`, and the covered entry drops below `STARTED` while still tappable. A
  second tap's event waits in the Channel ([ADR-0019](0019-navigation-as-one-time-events.md)) and fires
  on return, when the guard passes it: a double tap on a Home Favorite reopens its Details on return.
  Accepted.
- Where that harms, the screen drops the tap first: Preview ignores Start below `STARTED`, so returning
  from a session with nothing Studied never starts a second one.
