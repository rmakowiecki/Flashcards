# Screen showcase

A debug-only catalog of the app's real screens, each drawn full screen in a fixed, hand-built state filled with made-up sample data. It lets a developer look at any listed screen in any listed state without signing in, reaching it through the app, or waiting on the network. It exists only in debug and profiling builds and lives in `:debug-tools`.

## Where it opens

The debug hub has a Screen showcase row. It opens the showcase list in a screen of its own, outside the tab shell, so no screen in the showcase ever shows the bottom navigation bar, Home and the Study tab included ([navigation.md](navigation.md)). Back from the list returns to the debug hub.

The list groups its rows by where each screen sits in the app: Onboarding, Home, Browse and Study session. Each row is one screen in one state, and tapping it opens that state full screen. Back returns to the list.

## Rules

- **Real screens only.** Each entry draws the production screen's own stateless content, the same composable the app shows, so the showcase can never drift from the shipped look. It never copies or re-creates a screen.
- **State is built by hand.** Each entry's screen state is built directly from the domain models and the sample data. No ViewModel, repository, dependency injection, network, sign-in or permission prompt is involved, so an entry always renders the same way on any device.
- **The screen's own controls still work; its actions do nothing.** Anything the screen handles inside itself, such as paging through onboarding or opening the search bar, works as in the app. Every action the screen reports outward does nothing. The one exception is a screen's own back or close control where the screen would otherwise trap system back, such as Selection Mode on Category Details, whose back leaves Selection Mode: there it returns to the list.
- **Live inputs are fixed.** A screen that normally follows a live stream, such as the microphone level or a message stream, gets a fixed value or a stream that never emits.
- **Animations run.** Entrances, the card reveal and similar motion play as they do in the app. The Session Summary opens already scored, so it shows its settled screen rather than the reveal sequence ([session-summary.md](session-summary.md#animation-sequence)).
- **The system theme applies.** The showcase follows the device's light or dark setting, like the app.
- **Spoken sessions stay silent.** The read-aloud and Voice Answering entries show their playing and listening states, but no voice engine or microphone runs.

## Sample data

Every entry shares one made-up User and one made-up taxonomy:

- **The User** is Alex, with no photo, so the avatar shows initials, at a mid-curve Level with a Streak running.
- **Categories** cover developer topics: Kotlin, Android, Python, System Design and DevOps, each with realistic Topics. Kotlin, Android and Python carry the real Categories' glyphs and colors. System Design has a color but no glyph, and DevOps has neither, so both fallbacks are on show ([category-icon-color.md](category-icon-color.md)).
- **Flashcards** read like the real corpus: inline code, code blocks with real code, Difficulty across the range, and Extended Context on the harder cards.
- **Progress, Favorites and Recents** are filled in, so rings, bookmark badges, the Favorites carousel and the Recently studied list all show content. Recents are timed relative to when the entry opens.

## Entries

| Group | Entries |
|---|---|
| Onboarding | the gallery on its cover, paged through by hand to reach each step ([onboarding-flow.md](onboarding-flow.md)) |
| Home | Favorites and Recents loaded ([home.md](home.md)) |
| Browse | the Category list, whose search bar shows a fixed set of results for whatever is typed; Category Details; Category Details in Selection Mode; Subcategory Details ([browse.md](browse.md), [category-search.md](category-search.md)) |
| Study session | Preview of a drawn Quick Session; Rated at the question; Rated with the answer revealed; Rated with Voice Answering listening; Fast tapping through; Fast reading aloud; the Session Summary ([preview-study-session.md](preview-study-session.md), [rated-mode.md](rated-mode.md), [fast-mode.md](fast-mode.md), [session-summary.md](session-summary.md)) |

Splash, Login, Settings, Account and dialogs are not in the showcase.

## Production code

The showcase adds nothing to production screens. Its only change there is visibility: the Home and Onboarding contents are public, which the app does not need, so the showcase can call them from another module. A state a screen holds inside itself, such as an onboarding step or an open search bar, is reached by operating the screen by hand rather than through a parameter added for the showcase.

## Adding a screen

1. Make sure the screen's stateless content is public. Never add a parameter to a production screen for the showcase; a state the screen holds inside itself is reached by operating it by hand.
2. Build the state from the sample data, adding to the sample data when the screen needs something new.
3. Add an entry to the showcase list, with its label in `:debug-tools`'s own strings, under the group where the screen sits.
4. Give every action a no-op, and wire back only where the screen would otherwise trap it.

## Related

- [navigation.md](navigation.md): the tab shell the showcase stays out of, and the debug hub.
- [onboarding-flow.md](onboarding-flow.md), [home.md](home.md), [browse.md](browse.md), [category-search.md](category-search.md): the onboarding, Home and Browse screens shown.
- [preview-study-session.md](preview-study-session.md), [rated-mode.md](rated-mode.md), [fast-mode.md](fast-mode.md), [study-session.md](study-session.md), [session-summary.md](session-summary.md): the session screens shown.
- [category-icon-color.md](category-icon-color.md): the glyph and color fallbacks the sample Categories show.
- [ADR-0011](../adr/0011-multi-module-architecture.md): `:debug-tools` as an aggregator beside `:app`.
- [ADR-0067](../adr/0067-destinations-navigate-only-from-their-own-back-stack-entry.md): how the showcase's own screens navigate.
