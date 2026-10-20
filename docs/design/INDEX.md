# Design docs: index

One entry per doc under `docs/design/`. Find the topic you are working on or need more context on, then read the **whole** listed doc before changing or designing anything in that area. Rules for writing these docs: [README.md](README.md).

Entry format:

```
- [name.md](name.md): one or two sentences on what the doc covers.
  Read when: the tasks or areas that need this doc.
```

## Built

- [account.md](account.md): The Account screen opened from the Settings avatar: the collapsing identity header, Contact support, privacy policy, app version, sign-out, Account Deletion and its failure and interrupted cases, the Report a bug form, and the Open-source licenses screen.
  Read when: changing `feature:account`, sign-out, Account Deletion, Bug Reports or the support and legal rows.
- [browse.md](browse.md): The Study tab's Category list, Category Details with Selection Mode and its Quick and Custom session entry, the Studied progress rings, and Subcategory Details with its Filter and Sort dialogs, Start session and empty filtered state.
  Read when: changing `feature:browse`, the Category or Subcategory screens, Selection Mode, progress rings, or Subcategory Details filtering and sorting.
- [category-icon-color.md](category-icon-color.md): How each Category gets its icon glyph and accent color: the optional fields, fallbacks, tinting and glyph caching on each surface, shortcut icons, and the seed pipeline.
  Read when: touching Category icons or colors, the Category icon tile or inline glyph, or the category icon seed assets.
- [category-search.md](category-search.md): Prefix search over Categories and Subcategories on the Study tab: matching rule, result layout, chip lines, read budget, live behavior.
  Read when: changing Browse search, the search fields on Subcategories or Categories, or search result rows and chip lines.
- [curation-requests.md](curation-requests.md): Flagging a Flashcard for a content fix from a session: the flag icon, the Report a problem dialog and its Curation Actions, sending and its failure and offline handling, the resubmit skip, and what a report does not do.
  Read when: changing the Report a problem dialog, Curation Actions or the curation request write.
- [fast-mode.md](fast-mode.md): Fast Study Sessions: tap-through, the read-aloud loop, spoken text, and what next, previous, jump and play do.
  Read when: changing the Fast session screen or read-aloud.
- [firestore-schema.md](firestore-schema.md): The current Firestore data schema, field by field. The single source of truth for persisted shapes.
  Read when: any task that reads or writes Firestore, Cloud Functions payloads, or seed data.
- [flashcard-cache.md](flashcard-cache.md): Cache-first Flashcard reads and the seed-versioned invalidation that throws the cache away.
  Read when: changing Flashcard repositories, startup sync, or the seed version bump.
- [flashcard-selection.md](flashcard-selection.md): How a session's Flashcards are chosen on Preview: the Subcategories in scope (including the Quick Session sample), pool loading, filters, the draw and its order, when it reruns, and the duration estimate.
  Read when: changing card selection, the Quick Session sample, session filters, Length or Sort Order.
- [home.md](home.md): The Home tab: the Level card, the Favorites carousel and its cards, the Recently studied list and replay, how the sections load and reveal together, the empty states and the Favorites hint, and failure and Retry.
  Read when: changing `feature:home`, the Level card on Home, Favorite cards or Recent rows.
- [launcher-shortcuts.md](launcher-shortcuts.md): Launcher shortcuts to a Category or Subcategory: labels and icons, pinning from the Details screens, the dynamic set that mirrors Favorites, and how a tapped shortcut is resolved, including stale targets and the sign-in gate.
  Read when: changing app shortcuts, the pin action, the dynamic shortcut sync or shortcut launch routing.
- [navigation.md](navigation.md): How screens connect: launch routing through Splash, Onboarding and Login, the tab shell, the back stack around a Study Session, leaving for Login, and navigation as one-time events.
  Read when: adding a screen or route, changing launch routing, tabs or back-stack behavior, or writing a ViewModel that navigates.
- [onboarding-flow.md](onboarding-flow.md): The first-run onboarding gallery: its pager, chrome and background graph, each step, the on-device voice test and its microphone permission, Skip, and the commit with its Guest session, failure and offline handling.
  Read when: changing `feature:onboarding`, first-run behavior, or what onboarding writes.
- [preview-study-session.md](preview-study-session.md): The Preview Study Session screen: every entry point and what it sends, starting values, the hero and its states, the settings sheet and its rows, "keep as default", the microphone handshake and starting the session.
  Read when: changing `feature:study` preview, a session entry point, session settings, "keep as default" or the Voice Answering microphone flow.
- [rated-mode.md](rated-mode.md): Rated Study Sessions: manual and voice answering, Ratings and Attempts, the queue and re-insertion of weaker cards, skips and other moves that use no Attempt, and Terminal States.
  Read when: changing the Rated session screen, its queue, Attempts or rating rules.
- [screen-showcase.md](screen-showcase.md): The debug-only catalog of real screens in hand-built states: where it opens, its rules, the sample data, the entries, its visibility-only reach into production code, and how to add a screen.
  Read when: changing `:debug-tools`'s screen showcase or its sample data, or changing a showcased screen's stateless content signature.
- [session-submission.md](session-submission.md): What happens to a session once it ends: the Pending Session queue and its delivery, the server's one-transaction commit, and the Streak, Daily Goal and scoring-state rules.
  Read when: changing session submission or delivery, the Pending Session queue, streak, daily goal or session duration.
- [session-summary.md](session-summary.md): The Session Summary screen: handing the session over, server score versus local preview, the headline and stat pills, the XP breakdown, the Level card and its pour, the animation sequence, Back to Home and Study Again.
  Read when: changing the Summary screen, its submission or score fallback, its animation, or the Level card pour.
- [settings.md](settings.md): The Settings tab: its rows and dialogs, saving and save failure, the voice dialog, the other screens that save the same defaults, and how preferences are stored on the device.
  Read when: changing `feature:settings`, study session preferences, or where a default is saved.
- [study-session.md](study-session.md): What Rated and Fast sessions share: loading, the header, card and dialogs, the dialog hold, spoken sessions (background play, notification, lock-screen and headset controls, audio interruptions, engine failure), leaving a session, and termination.
  Read when: changing any session screen behavior common to both modes, the voice/media service, transport controls or audio interruption handling.
- [voice-answering.md](voice-answering.md): Voice Answering in Rated sessions: the round, the answer panel, notices and pauses, transport by phase, capture and Bluetooth routing, on-device obfuscation and privacy, the streamed transcribe-and-grade backend, entitlement.
  Read when: changing `core:voice`, the voice grading callables in `functions/`, or Voice Answering behavior.
- [voice-capture-indicator.md](voice-capture-indicator.md): The animated microphone indicator: how the live input level is measured in decibels, smoothed, shaped into a travelling wave of bars and drawn, and how it follows playback in the voice test.
  Read when: changing the microphone indicator, the input level, or where the indicator shows.
- [voice-obfuscation.md](voice-obfuscation.md): The on-device voice transform applied to every spoken answer: the random pitch and formant shift, the time-stretch and resample math, what leaves the device, and the limits of the protection.
  Read when: changing `core:voice` obfuscation, the privacy claims about voice, or what audio is sent for grading.
- [website.md](website.md): The public website on Firebase Hosting: its three pages and their addresses, how links and history move between them, the brand-gradient look, the loading fallback and the limits of a canvas-drawn site.
  Read when: changing `:web`, the hosted pages, their addresses or Firebase Hosting.
- [xp-leveling-system.md](xp-leveling-system.md): XP sources and their rules, the XP configuration, the Level curve, and how XP and Level are stored.
  Read when: changing XP awards, the XP configuration, the Level curve or Level-floor clamping.

## Planned

Read these only when working on the feature they describe.

- [planned/launcher-widget.md](planned/launcher-widget.md): A home screen widget showing study progress at a glance: its two fixed sizes, where its figures come from, its states, when it refreshes, and what a tap does in each app state.
  Read when: starting the launcher widget, or changing an event that should refresh it.
- [planned/mastery-defense.md](planned/mastery-defense.md): A floor of previously mastered Flashcards in each session's draw (both Study Modes), and the Rated-only shield marker.
  Read when: working on Mastery Defense card selection or the shield.
- [planned/private-flashcards.md](planned/private-flashcards.md): User-created Private Flashcards in a Subcategory: creating, editing and deleting them, the Private only filter, how they behave in a session, and loading.
  Read when: working on Private Flashcards, or on anything that reads a Subcategory's Flashcards or records a session.
- [planned/progress-dashboard.md](planned/progress-dashboard.md): The Progress tab: Level card, Daily Goal, Streak, summary figures, history chart and per-Category breakdown, where each figure is read from, and how stale values and Pending Sessions are handled.
  Read when: starting the Progress tab, or adding the activity aggregate to session submission.
- [planned/voice-answering.md](planned/voice-answering.md): Syncing the premium entitlement record from Play Billing purchases.
  Read when: working on Voice Answering entitlement or billing.
