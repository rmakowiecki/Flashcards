# Navigation as one-time events: Channel + receiveAsFlow, not persistent state

## Status

Accepted. **Supersedes** the state-field navigation approach previously documented in
`docs/navigation-pattern.md` (nullable `navigationDestination` on screen state + `onNavigationHandled()`).

## Decision

Navigation is modeled as a **one-time side effect**, never as persistent UI state:

- Each ViewModel that triggers navigation owns a `private val eventChannel = Channel<XxxDestination>(Channel.BUFFERED)`
  and exposes `val events = eventChannel.receiveAsFlow()`.
- Navigation is dispatched from a handler with `viewModelScope.launch { eventChannel.send(XxxDestination.Foo) }`.
- The `XxxScreen` composable collects it exactly once via the shared `ObserveAsEvents(viewModel.events) { … }`
  helper (`core:ui`, `core/ui/.../navigation/ObserveAsEvents.kt`), and calls the nav callback inside the `when`.
- Destinations remain **type-safe sealed interfaces** (no route strings). Every per-screen destination
  interface implements the marker `NavigationEvent` (`core:ui`, `core/ui/.../navigation/NavigationEvent.kt`).
- There is **no** `navigationDestination` field on screen state and **no** `onNavigationHandled()` reset method.

`Channel.BUFFERED` queues events emitted while no collector is attached (e.g. mid-recomposition or before
the composable attaches), and `receiveAsFlow()` is uni-cast — each event is delivered exactly once.
`ObserveAsEvents` collects under `repeatOnLifecycle(STARTED)` on `Dispatchers.Main.immediate`.

## Context

The original pattern stored the next destination as nullable state and observed it with
`LaunchedEffect(state.navigationDestination)`, requiring the ViewModel to null it out via
`onNavigationHandled()` after each navigation. That treats a fire-once effect as durable state:
it needs explicit reset bookkeeping, risks re-firing a stale destination on recomposition or
config change if the reset is missed, and conflates "where the user is going" (an event) with
"what the screen currently shows" (state). Splash additionally had to derive a `StateFlow<SplashDestination?>`
from a `combine` purely to hand a nullable value to `LaunchedEffect`.

The reference pattern (dedicated `Channel(BUFFERED)` exposed via `receiveAsFlow()`, collected once in the
UI for one-off events) removes all of that: no reset, no stale re-fire, and navigation stops leaking into
persistent state.

## Rationale

- **Idempotency by delivery, not by reset.** Uni-cast `receiveAsFlow()` delivers each event once; we no
  longer null a field to prevent replays.
- **No lost events.** `Channel.BUFFERED` buffers across the brief windows when no collector is active,
  which is exactly the failure mode that made a *default* `SharedFlow` (replay=0) unsuitable in the prior ADR.
- **State stays state.** Screen state classes describe only what is rendered; navigation is a separate,
  transient concern.

## Alternatives considered

- **Keep the nullable-`navigationDestination` state field** (prior approach) — rejected; requires
  `onNavigationHandled()` reset and risks stale re-fires. This ADR reverses it.
- **`SharedFlow` for navigation** — rejected historically for dropping events with default config; the
  buffered `Channel` addresses that while keeping single-delivery semantics.
- **Wrap destinations in a per-screen `XxxEvent` type** — deferred; the `Channel` carries the existing
  `XxxDestination` sealed type directly. Snackbar/toast transient events remain separate (see
  `SharedFlow` for snackbars/toasts).

## Consequences

- New `core:ui` primitives: `NavigationEvent` marker + `ObserveAsEvents` composable, reused by every feature.
- `docs/navigation-pattern.md` rewritten to this pattern; `AGENTS.md` §Async navigation bullet updated to match.
- Every screen that navigates from a ViewModel handler (Home and CategoryDetails included) follows this
  pattern; none holds navigation in ViewModel state.
