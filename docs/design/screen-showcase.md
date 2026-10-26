# Screen showcase

A debug-only catalog of the app's real screens, each drawn full screen in a fixed, hand-built state filled with sample data. It lets a developer look at any listed screen in any listed state without signing in, reaching it through the app, or waiting on the network. It exists only in debug and profiling builds and lives in `:debug-tools`.

## Where it opens

The debug hub has a Screen showcase row. It opens the showcase list in a screen of its own, outside the tab shell, so no screen in the showcase ever shows the bottom navigation bar, Home and the Study tab included ([navigation.md](navigation.md)). Back from the list returns to the debug hub.

The list groups its rows by where each screen sits in the app: Onboarding, Home, Browse and Study session. Each row is one screen in one state, and tapping it opens that state full screen. Back returns to the list.

## Rules

- **Real screens only.** Each entry draws the production screen's own stateless content, the same composable the app shows, so the showcase can never drift from the shipped look. It never copies or re-creates a screen.
- **State is built by hand.** Each entry's screen state is built directly from the domain models and the sample data. No ViewModel, repository, dependency injection, network, sign-in or permission prompt is involved, so an entry always renders the same way on any device.
- **Dialogs and simple switches work; actions that change data do nothing.** Anything the screen handles inside itself, such as paging through onboarding or opening the search bar, works as in the app. Each entry also keeps its screen state locally, so a dialog opens, follows every edit to its draft and closes on back, scrim, Cancel or Confirm. Confirm applies nothing: the screen behind keeps its values, and reopening the dialog starts from them. The Favorite bookmark and Category Details' Selection Mode (entering it, checking rows, select all, leaving it) also work locally. Every other action, such as starting a session, navigating, rating a card or saving a setting, does nothing. Back closes an open dialog or leaves Selection Mode first, then returns to the list.
- **Live inputs are fixed.** A screen that normally follows a live stream, such as the microphone level or a message stream, gets a fixed value or a stream that never emits.
- **Animations run.** Entrances, the card reveal and similar motion play as they do in the app. The Session Summary opens already scored, so it shows its settled screen rather than the reveal sequence ([session-summary.md](session-summary.md#animation-sequence)). Its two other entries hold that sequence still on one step instead: the XP breakdown with every tile already listed, and the reveal with one frame of the confetti burst frozen over the total.
- **The system theme applies.** The showcase follows the device's light or dark setting, like the app.
- **Spoken sessions stay silent.** The read-aloud and Voice Answering entries show their playing and listening states, but no voice engine or microphone runs.

## Sample data

The content is real, exported once from Firestore into `:debug-tools`'s raw resources and read when the showcase opens. The User's activity is made up.

- **Categories and Topics** are the whole real taxonomy, with each Category's real glyph and color ([category-icon-color.md](category-icon-color.md)) and each Topic's real card count. The Study tab's search results show a search for "con", less Apple Platforms and every Concurrency Topic but Swift's.
- **Flashcards** are a sample of real cards for the three Topics a screen lists: 15 of Kotlin Coroutines on Subcategory Details, 20 of Kotlin Functions in the Rated session and 20 of Android Compose in the Fast session. Each sample spans the Difficulty range and includes code blocks and Extended Context where the Topic has them. Subcategory Details shows the Topic's real total of 194 above its 15 listed cards.
- **The User** is Ross, with no photo, so the avatar shows initials, at Level 14 with a Streak running.
- **The Session Summary** is a Rated session that crossed into Level 14, ending where Home's Level card stands. Its breakdown has every award but a de-mastery, at the default XP configuration's rates. It carries a Streak award, so it is the day's first session, and it runs longer than the Daily Goal so that it earns the Daily Goal award on its own.
- **The graded voice answer** is made up: a paraphrased spoken answer to a real card, graded Correct on a second Attempt after a Failed first.
- **Extra Tags** are the one hand edit to the exported content, made in the raw resource itself, never in code. The cards on screen in the Rated and Fast entries had a single Tag each, so each got two more for a fuller chip row, picked from Tags already used in its Category.
- **Progress, Favorites and Recents** are filled in, so rings, bookmark badges, the Favorites carousel and the Recently studied list all show content. Recents are timed relative to when the entry opens. The two newest are today's: they end before the entry opens and start no earlier than midnight, and the Daily Goal's minutes are their durations added up, so the ring always matches the rows. It is part done.

## Entries

| Group | Entries |
|---|---|
| Onboarding | the gallery on its cover, paged through by hand to reach each step ([onboarding-flow.md](onboarding-flow.md)) |
| Home | the Streak and Daily Goal row, Favorites and Recents loaded ([home.md](home.md)) |
| Browse | the Category list, whose search bar shows a fixed set of results for whatever is typed; Category Details; Category Details in Selection Mode, with the Category not bookmarked; Subcategory Details; Subcategory Details with the Filter dialog open, two Tags selected and Difficulty narrowed ([browse.md](browse.md), [category-search.md](category-search.md)) |
| Study session | Preview of a drawn Quick Session; the same Preview with its settings sheet open; Rated at the question; Rated with the answer revealed, on a card with a code block and Extended Context; Rated with Voice Answering listening; Rated with a voice answer graded; Fast tapping through; Fast reading aloud; the Session Summary settled; the Session Summary's XP breakdown; the Session Summary's reveal with confetti ([preview-study-session.md](preview-study-session.md), [rated-mode.md](rated-mode.md), [voice-answering.md](voice-answering.md), [fast-mode.md](fast-mode.md), [session-summary.md](session-summary.md)) |

Splash, Login, Settings and Account are not in the showcase. The Subcategory Details Filter dialog is the one dialog with its own entry; any other dialog of a listed screen is opened by hand.

## Production code

**The showcase never changes production code except to widen visibility.** It adds no parameter, overload, branch, flag or hook to a production screen, and it changes no production behavior. What it needs that the app does not is made public, so the showcase can call it from another module:

- `HomeContent`, `OnboardingContent` and `OnboardingActions`.
- The Session Summary's `SummaryLayout`, `SummarySequence` and `SummaryPhase`, so an entry can hold the sequence on one step, and `drawBurst`, so it can draw one frame of the confetti.

A state a screen holds inside itself, such as an onboarding step or an open search bar, is reached by operating the screen by hand. A parameter that already exists, such as the Preview's sheet-open flag kept for IDE previews, may be used.

## Adding a screen

1. Make sure the screen's stateless content is public. Widening visibility is the only change allowed in production code: never add a parameter to a production screen for the showcase. A state the screen holds inside itself is reached by operating it by hand.
2. Build the state from the sample data. New content comes from Firestore into the raw resources, never written by hand.
3. Add an entry to the showcase list, with its label in `:debug-tools`'s own strings, under the group where the screen sits.
4. Hold the state in `mutableStateOf` and pass `showcaseDialogAfter(event)` as the dialog on every dialog event. Handle a simple switch the same way, locally. Give every other action a no-op.

## Related

- [navigation.md](navigation.md): the tab shell the showcase stays out of, and the debug hub.
- [onboarding-flow.md](onboarding-flow.md), [home.md](home.md), [browse.md](browse.md), [category-search.md](category-search.md): the onboarding, Home and Browse screens shown.
- [preview-study-session.md](preview-study-session.md), [rated-mode.md](rated-mode.md), [voice-answering.md](voice-answering.md), [fast-mode.md](fast-mode.md), [study-session.md](study-session.md), [session-summary.md](session-summary.md): the session screens shown.
- [category-icon-color.md](category-icon-color.md): the glyphs and colors the sample Categories carry.
- [ADR-0011](../adr/0011-multi-module-architecture.md): `:debug-tools` as an aggregator beside `:app`.
- [ADR-0067](../adr/0067-destinations-navigate-only-from-their-own-back-stack-entry.md): how the showcase's own screens navigate.
