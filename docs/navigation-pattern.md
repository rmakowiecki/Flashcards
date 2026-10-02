# Navigation Pattern

Navigation is a **one-time side effect**, delivered through a `Channel` and collected once in the UI —
never merged into persistent screen state. Destinations stay type-safe sealed interfaces (no route
strings). See [ADR-0019](adr/0019-navigation-as-one-time-events.md) for the rationale (this supersedes
the earlier state-field / `SharedFlow` discussion).

## Composable Taxonomy

- `XxxScreen` — nav-aware entry point; holds ViewModel, observes state, collects nav events
- `XxxContent` — stateless UI; accepts state and callbacks, no ViewModel, fully previewable

## Rules

- Each screen has a `XxxDestination` sealed interface implementing `NavigationEvent`
  (`core:ui`, `core/ui/.../navigation/NavigationEvent.kt`), listing its possible next destinations.
- The ViewModel owns `private val eventChannel = Channel<XxxDestination>(Channel.BUFFERED)` and exposes
  `val events = eventChannel.receiveAsFlow()`.
- Navigation handlers dispatch via `viewModelScope.launch { eventChannel.send(XxxDestination.Foo) }`.
- `XxxScreen` collects with `ObserveAsEvents(viewModel.events) { … }`
  (`core:ui`, `core/ui/.../navigation/ObserveAsEvents.kt`) and calls the nav callback inside.
- **No** `navigationDestination` field on screen state and **no** `onNavigationHandled()` — delivery is
  once-only, so there is nothing to reset.

## Why a buffered Channel (not state, not plain SharedFlow)

- `receiveAsFlow()` is uni-cast: each event is delivered exactly once, so navigation can't re-fire on
  recomposition or config change — no reset bookkeeping.
- `Channel.BUFFERED` queues events emitted while no collector is attached (mid-recomposition, or before
  the composable attaches), which a default `SharedFlow` (replay=0) would drop.
- Screen state describes only what is rendered; navigation is a transient concern kept out of it.

Snackbars/toasts remain separate transient events and are not part of this navigation channel.

## File layout per screen

- `XxxDestination.kt` — sealed interface `: NavigationEvent`, lists possible nav targets
- `XxxScreenState.kt` — UI state data class (no navigation field)
- `XxxViewModel.kt` — `eventChannel` + `events`; sends destinations from handlers
- `XxxScreen.kt` — contains both `XxxScreen` (nav entry point) and `XxxContent` (stateless UI)

## Shared infra (`core:ui`)

```kotlin
// core/ui/.../navigation/NavigationEvent.kt
interface NavigationEvent

// core/ui/.../navigation/ObserveAsEvents.kt
@Composable
fun <T> ObserveAsEvents(events: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent = rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                events.collect { currentOnEvent.value(it) }
            }
        }
    }
}
```

## Example

```kotlin
// XxxDestination.kt
sealed interface XxxDestination : NavigationEvent {
    data object Home : XxxDestination
    data class Details(val id: String) : XxxDestination
}

// XxxViewModel.kt
private val eventChannel = Channel<XxxDestination>(Channel.BUFFERED)
val events = eventChannel.receiveAsFlow()

fun onItemClick(id: String) {
    viewModelScope.launch { eventChannel.send(XxxDestination.Details(id)) }
}

// XxxScreen.kt — nav entry point
@Composable
fun XxxScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
    viewModel: XxxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { destination ->
        when (destination) {
            XxxDestination.Home -> onNavigateToHome()
            is XxxDestination.Details -> onNavigateToDetails(destination.id)
        }
    }

    XxxContent(state = state, onItemClick = viewModel::onItemClick)
}
```

Every screen whose ViewModel decides where to go (e.g. Home, CategoryDetails) uses this channel, even for
a plain tap that maps to one destination; the Screen composable is the only place that calls the nav callbacks.
