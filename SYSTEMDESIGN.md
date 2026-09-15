# Flashcards App — System Design

## Navigation

4 top-level tabs:
```
🏠 Home · 📚 Study · 📊 Progress · ⚙️ Settings
```

### Nav graph structure

```
Root NavHost
├── Splash
├── Login
└── AuthedGraph  ← all auth-required screens; popUpTo<AuthedGraph>(inclusive=true) on sign-out. See ADR-0002.
    ├── Main  ← bottom nav shell
    │   ├── HomeGraph (nested graph)
    │   │   └── HomeRoot
    │   ├── StudyGraph (nested graph)
    │   │   └── StudyRoot
    │   ├── ProgressGraph (nested graph)
    │   │   └── ProgressRoot
    │   └── SettingsGraph (nested graph)
    │       └── SettingsRoot
    ├── CategoryDetails(categoryId, categoryName)                    ← full-screen, no bottom nav; shared by Home + Study
    ├── SubcategoryDetails(categoryId, categoryName, subcategoryId, subcategoryName)  ← full-screen, no bottom nav; shared by Home + Study
    ├── PreviewStudySession(categoryId, categoryName, subcategoryIds, subcategoryNames, filterTagIds, isQuickSession)
    ├── RatedStudySession(categoryId, sessionTitle, subcategoryIds, cardIds, voiceAnsweringEnabled, attemptsPerCard, partialEndsCard)
    ├── FastStudySession(categoryId, sessionTitle, subcategoryIds, cardIds, readAloudEnabled, speechRate, voiceId)
    ├── StudySessionSummary(sessionId, result)  ← mandatory fresh-session egress for both Study Modes, natural end or premature exit; never used to view a past session (a future past-session detail view is a separate screen/route)
    └── CreatePrivateFlashcard(subcategoryId)
```

Screens in `AuthedGraph` outside `Main` are full-screen (no bottom nav visible).

### Shared screens (CategoryDetails, SubcategoryDetails)

Accessible from both Home and Study tabs. Registered at the root NavHost level (siblings of Main), so they appear full-screen with no bottom navigation bar. A single route type serves both ingresses — navigation uses the root NavController passed down through MainScreen callbacks.

### Session entry routing

All session entry points navigate to `PreviewStudySessionScreen`, which owns card selection from the given scope. See [ADR-0004](docs/adr/0004-preview-study-session-screen-owns-card-selection.md).

- **Study Again (All)**: → `PreviewStudySessionScreen` with same params, `popUpTo<Main>()`.
- **Study Again (Failed)**: → `RatedStudySession` directly with `cardIds = [failedCardIds]`, `popUpTo<Main>()`. Unambiguous by construction — a failed-card replay is always Rated.
- **Back to Home / system back from StudySessionSummary**: `popUpTo<Main>(inclusive = false)` — returns to whatever tab was active.

### Cross-tab navigation

Home empty state CTA ("Start your first session") triggers a tab switch to Study via callback wired in `MainScreen`. No cross-NavController state sharing.

## Home Screen

- Greeting with user's display name
- **Recents** carousel — past Study Sessions, read `users/{uid}/sessions orderBy(startTimestamp, DESCENDING) limit(n)`, two card variants (**designed, not yet built**: no `sessions` write, no query, no carousel code exists in `feature/home` today — see Session Termination):
  - *Single-subcategory*: shows Subcategory + Category name; taps into Subcategory Details
  - *Composite*: shows Category name only; taps into Category Details
- **Favorites** carousel — bookmarked Subcategories; each card shows Subcategory + Category name; taps into Subcategory Details
- Sections with no content are hidden individually
- Empty state (both carousels empty): illustration + "Start your first session" CTA → navigates to Study tab

## Study Screen

- Scrollable list of all top-level Categories as UI cards (default state)
- Each Category card shows:
  - Category name
  - Preview row of Subcategories (labeled **Topics** in the UI)
  - Total Topic count (e.g. "12 topics")
- Search bar filters in real time, matching against **Category name and Subcategory name only** (no Tags, no Flashcard content)
- Min query length: 2 characters — shorter queries show the default Category list
- Search results render in two sections:
  - **Topics** (shown first) — one compact row per matched Subcategory; row shows `Subcategory · Category` breadcrumb; tapping → Subcategory Details (single-subcategory intent)
  - **Categories** (shown second) — Category cards in their normal shape; tapping → Category Details
- A Category card appears in the Categories section when either its name matches or any of its Subcategories match
- Category card preview row in search results shows the default first-N Subcategories (matched Topics already surface as Topic rows above — no duplication)
- Sort order: Topics by match quality (exact > prefix > substring, tiebreak alphabetical); Categories by Firestore `order` field
- No-match empty state: illustration + copy quoting the query (e.g. "No topics or categories match 'xyz'")
- No tag-based filtering; search only

## Category Details Screen

- Lists all Subcategories (labeled **Topics**) for the Category
- Three session-start options:
  - **Quick Session** button — system auto-selects Subcategories → Preview Study Session Screen → composite Study Session begins
  - **Start Custom Session** button — transforms the list into multi-select mode; user selects Topics; "Start" button becomes active after ≥1 selected → Preview Study Session Screen → Study Session begins (composite when ≥2 Topics are selected, single-subcategory otherwise)
  - **Fast-start action on each Topic row** — routes directly to Preview Study Session Screen for that Subcategory (skips Subcategory Details), without navigating away from Category Details
- Tapping a Topic row (not its fast-start action) → Subcategory Details screen

## Subcategory Details Screen

- Lists all Flashcards belonging to the Subcategory as collapsible items (collapsed: question + tag chips; expanded: + answer)
- App bar: back navigation + bookmark action + overflow menu (placeholder today for a near-future dynamic launcher shortcut item, see `docs/design/launcher-shortcuts.md`)
- **Bottom toolbar** (always visible, floating): Filter icon, Sort icon, Add(+) icon (create Private Flashcard), then a trailing extended-pill FAB, "Start Session" → Preview Study Session Screen with `filterTagIds` = currently active Tags → single-subcategory Study Session begins. See [ADR-0022](docs/adr/0022-subcategory-details-filter-sort-toolbar.md).
  - **Filter** — single entry point opening one sheet with Tags (multi-select, OR semantics, chips derived `distinct(card.tags)` plus a **"Private"** chip) and a Difficulty `RangeSlider` (1–10); the two facets AND-combine. Active Tags/difficulty filter both the visible Flashcard list and the Study Session pool. Badge dot when non-default.
  - **Sort** — menu with **Default | Easiest first | Hardest first**, mutually exclusive; reorders the visible list only, never changes which/how many cards are shown. Badge dot when non-default.
- Filtering to zero results is a designed-but-not-yet-implemented empty state (clear-filters action, Start-session FAB disabled).

## Preview Study Session Screen

Full-screen modal that precedes every Study Session. Receives `categoryId`, `categoryName`, `subcategoryIds`, `subcategoryNames`, `filterTagIds: List<String>` (empty by default), and `isQuickSession: Boolean` (false by default). A read-only hero shows session scope: card count, estimated duration, and topic count (multi-topic sessions only). Below it, a **plain column** (not yet the sheet chrome below) presents each adjustable setting as a summary row — each shows its current value and opens a focused `FlashcardsDialog`-based dialog. Rows and visibility, as actually built:

- **Mode** — Rated | Fast (default Rated). Always shown.
- **Voice answering** — On | Off. Rated sessions only.
- **Length** — session card count. Always shown.
- **Filters** — merged tags + difficulty (ADR-0022 shape; tags OR-within, AND-combined with a difficulty range). Tag facet appears for single-subcategory sessions only; multi-topic sessions filter by difficulty only. Always shown.
- **Sort** — Default | Easiest first | Hardest first. Controls presentation order of the already-selected cards within the session (Easiest/Hardest reorder by difficulty; Default is the selection algorithm's natural output order). Does not influence which cards get selected — that's the card selection algorithm below, unaffected by this row. Always shown.

**Not yet built (ADR-0030 designs both):** a persistent no-scrim bottom-sheet chrome wrapping these rows, and a separate **Voice** row (TTS voice + speed, shown for Fast or Rated+voice-answering-on) — today that setting is reachable only from the session cog and the Settings screen.

Each edit popup carries a "keep as default" checkbox (persists a global default; unchecked is session-scoped), except the Filters popup (tags + difficulty always session-scoped) — though note the persistence write itself is a `TODO`, see Settings Screen below. "Start session" launches the session with the chosen settings; a "Re-randomize" button (multi-topic and Quick sessions only) re-samples Subcategories for Quick Sessions and re-runs card selection over the current pool for other multi-subcategory sessions. There is no session seed: every randomizing unit takes an injected `kotlin.random.Random` defaulting to `Random.Default`, and a Quick Session's sampled Subcategories are held as screen state so that adjusting a filter, length or sort does not silently re-roll which topics are being studied — only Re-randomize does. See [ADR-0030](docs/adr/0030-preview-session-settings-sheet.md) and [docs/design/study-session-preview-sheet.md](docs/design/study-session-preview-sheet.md).

This is the only place Study Mode (and, up front, Voice answering) is chosen for a concrete session — onboarding and the Settings screen only set the persisted default preference, neither starts a session. The "keep as default" checkbox on each Preview popup (see above) is what lets this screen also update that persisted default, without leaving session scope.

**Card selection algorithm (runs on Preview Study Session Screen):**
1. Fetch all Flashcards for the given `subcategoryIds`
2. If `filterTagIds` is non-empty: filter to cards where `card.tags` intersects `filterTagIds` (OR semantics — a card qualifies if it carries any of the active Tags)
3. Filter to cards where `card.difficulty` falls within the selected difficulty range (inclusive on both ends), AND-combined with the tag filter
4. Apply scoring and selection (MVP: random shuffle; target: performance-weighted sort → pick top N — see "Flashcard Selection Algorithm" below for the scoring formula)
5. Pass resolved `cardIds` to the chosen session route (`FastStudySession`/`RatedStudySession`); presentation order within the session is then set independently by the Preview screen's Sort row (Default/Easiest/Hardest), not by this selection step

`filterTagIds` is only ever non-empty for single-subcategory sessions launched from Subcategory Details. All other entry points (Quick Session, fast-start, Custom) pass `filterTagIds = emptyList()`.

## Study Session Flow

A session is scoped to one Category and one or more Subcategories. Card selection and Study Mode are determined on the Preview Study Session Screen before the session begins. If the available Flashcard pool is smaller than the configured session size N, the session starts with however many Flashcards exist — no warning shown.

Fast and Rated are **two separate screens, ViewModels and routes** — Study Mode cannot change mid-session, and the two share a card deck, a top bar and a set of dialogs but not an interaction model. See [ADR-0045](docs/adr/0045-separate-fast-and-rated-session-screens.md).

**Study Modes:**
- **Rated**: user reveals each answer manually, then self-rates (Failed / Partial / Correct). Each Flashcard accumulates Attempts until it reaches a Terminal State — **Mastered**, **Partial** or **Failed**, decided by the best Rating it achieved ([ADR-0044](docs/adr/0044-three-valued-terminal-state.md)).
- **Fast**: cards advance question → reveal answer → next, manually (tap to reveal, tap to advance) by default, or with read-aloud enabled: system TTS reads the question (`questionSpoken` if present, else `question`), pauses 1 500 ms, reads the answer (`answerSpoken` if present, else `answer`), pauses 2 500 ms, then auto-advances. When read-aloud is on, user controls: pause/play, skip-next, skip-previous, speech-rate slider (0.5×–2×), Show Answer (interrupts question, reads answer immediately). Playback persists with screen off or app backgrounded via `StudySessionVoiceService` (foreground `mediaPlayback` service). Persistent `MediaStyle` notification + lock-screen controls via `MediaSessionCompat`. No Ratings, no Attempts, no Terminal States — a Fast session's only per-card record is that a Flashcard became **Studied**, written once its answer has been shown ([ADR-0016](docs/adr/0016-card-progress-model.md)). See [ADR-0012](docs/adr/0012-tts-mediasession-stack-for-fast-mode.md).

**Fast mode entry point:** Study Mode is chosen exclusively on the Preview Study Session Screen (ADR-0004), which routes to `FastStudySession` or `RatedStudySession` accordingly. Read-aloud is an opt-in toggle on that screen (Voice row); when on, the session auto-starts voice playback once its cards are loaded (requesting the notification permission first on Android 13+), otherwise the session is manual tap-to-reveal/advance. Voice is tied to the session screen lifetime; navigating away stops playback.


> **Known bug:** with voice inactive, the sheet renders the Failed / Partial / Correct rating buttons unconditionally (`StudySessionScreen.kt:610-619`), so a Fast-manual session shows Rating controls for a mode that has no Ratings. The screen split above removes this class of bug structurally.

### Flashcard Mechanics

**Bottom sheet (persistent, fixed height, no scrim):**
- **Question state**: single "Show answer" CTA centered in the sheet
- **Answer state**: "How well did you know it?" label + **Failed · Partial · Correct** buttons
- Transition between states: fade cross-dissolve (~150ms)

**Card reveal (question → answer):**
- Triggered by tapping "Show answer" in the sheet or swiping up on the card
- Card expands downward; question text shrinks and animates upward
- Answer content fades in and shoots up into view
- QUESTION / ANSWER labels change color on reveal
- Overflowing content (long text, code blocks) scrolls inside the card; bottom sheet stays pinned

**Attempt label:** "Attempt X of N" visible in both question and answer states (N = user's configured Attempts limit, Settings — default 3, max 5) — planned, not yet implemented. Rated only.

**Progress indicator:** designed as **"X/N mastered"** (X incrementing on "Correct" tap) — **not implemented**; the top app bar today shows a plain "current / total" card-index counter (e.g. "3 / 18"), unrelated to Rating outcome. Because re-insertion grows the Rated queue, N must count **distinct Flashcards**, not queue entries.

**After rating:** auto-advances immediately to next card — no "Next" button. Transition: current card slides left + scales down + fades out; next card slides in from right + scales up + fades in. Sheet fades back to "Show answer" state for the new card.

**Exit:** X button (top-left) only — no in-session "Finish session" button. X always shows a confirmation dialog warning that session progress will be lost.

**Flag icon:** flag `IconButton` in the study session **top app bar**, left of the "3/18" card counter (not on the card itself — the bottom sheet is already crowded with mic, cog and transport controls). Shown only while a card is displayed. Tapping opens the **"Report a problem"** dialog (`FlashcardsDecisionDialog`): all 7 Curation Actions as toggleable checkboxes — Raise the difficulty, Lower the difficulty, Wrong tags, Needs a code example, Formatting looks broken, Needs a full rewrite, Duplicate or low quality — plus Cancel/Submit. Raise/Lower the difficulty are mutually exclusive: checking one clears the other; the other five toggle independently. The draft always starts empty (never seeds from the card's existing report); Submit upserts the checked Curation Actions to `users/{uid}/curationRequests/{cardId}` in one write. **No withdrawal path exists** — unchecking a row no longer removes anything from a previous submission, it only affects the current draft; Cancel discards the draft. The card continues in the session queue — no suppression. See [ADR-0017](docs/adr/0017-curation-report-system.md).

### Re-insertion Rules (Rated only; planned, not yet implemented — current code advances to next card on any Rating and discards the Rating value entirely)

- **Correct** (any Attempt): Flashcard exits queue → Terminal State Mastered
- **Partial** or **Failed**: Flashcard re-inserted with Attempt count incremented, at `currentIndex + random(gap)` — gap **2–4** for Failed, **5–9** for Partial, so weaker recall returns sooner. Clamped to the queue end; appended if fewer cards remain than the drawn gap. The draw uses an injected `kotlin.random.Random` defaulting to `Random.Default`, not a session seed — a predictable re-ask rhythm is something a user could learn to anticipate. See [ADR-0046](docs/adr/0046-failed-and-partial-re-insertion-placement.md)
- Each Flashcard has a maximum of N Attempts (user-configurable in Settings, default 3, max 5). A Voice Answering silence timeout consumes no Attempt
- The Flashcard's **Terminal State is the best Rating it ever achieved**: Correct → Mastered, else Partial → Partial, else Failed. Reaching the Attempts limit resolves the accumulated Ratings; it does not force Failed
- **"Partial ends the card"** setting (default off): when on, a Partial Rating resolves the Flashcard to Terminal Partial immediately instead of re-inserting it

### Session Termination

**Not yet implemented — this whole section is target design.** Today `onRating()` does nothing but advance to the next card and discards the Rating (no re-insertion, no Attempt tracking — see Flashcard Mechanics above), and both natural end and premature exit resolve to the same `onNavigateBack()` call with **no Firestore write of any kind**.

Designed. Both Study Modes terminate the same way: the session seals its `cardResults`, stamps `durationSeconds`, hands the result to the Summary screen, and writes nothing itself.

- **Natural end**: Rated — the queue empties (every Flashcard reached a Terminal State). Fast — the last card's answer has been shown. Both navigate to the Session Summary screen
- **Premature exit** (X button → confirm dialog): the result carries everything accumulated so far, flagged `isAbandoned`. The rule differs by mode:
  - **Rated** — a queued Flashcard the user never reached is simply absent from `cardResults`, but a queued Flashcard that already completed at least one Attempt is force-resolved into `cardResults` using its best rating so far (the same best-rating rule natural resolution uses) rather than discarded — it already satisfies **Studied**, so losing it at exit would contradict that definition
  - **Fast** — has no Attempts or Ratings, so it has no best-rating-so-far to force-resolve. Every Flashcard whose answer was shown becomes a `{ state: Seen }` entry in `cardResults`; a Flashcard the user never reached is simply absent
  
  Both navigate to Summary
- The exit-confirmation dialog **is already built** (`StudySessionDialog.ExitSession`); it currently pops the back stack instead of routing to Summary
- App kill during session: session is lost, no data saved, no resumption

### Data Saving

**Not yet implemented** — none of these writes exist in code today; kept here as target design once Re-insertion Rules and Session Termination land.

Everything is written **once, at the Session Summary screen, in a single atomic batch** ([ADR-0014](docs/adr/0014-session-stats-written-at-summary-screen.md)). Nothing is written while a session runs.

The batch contains:
- `users/{uid}/sessions/{sessionId}` — the Session Result, with its per-card `cardResults` map embedded. The document's own shape follows its `studyMode`: a Rated document carries Mastered/Partial/Defended/De-mastered counts and its `cardResults` entries carry Attempts used and a previously-mastered flag; a Fast document has none of those fields at all, not zeroed ones — Fast has no Ratings, Attempts or mastery to report
- `users/{uid}/progress/details/subcategories/{subcategoryId}` — one packed progress document per Subcategory touched ([ADR-0016](docs/adr/0016-card-progress-model.md))
- `users/{uid}/progress/summary` — nested-key `masteredCount` / `studiedCount` increments
- `users/{uid}/progress/user-stats` — `xp`, `level`, `xpIntoCurrentLevel`, `currentStreak`, `bestStreak`, `lastStudyDate`, `goalMetDate`

**This four-write count is the end state**, once `progress/user-stats` exists. Until then a single-Subcategory session is **three writes** — session, `progress/details/subcategories/{subcategoryId}`, `progress/summary` — and `progress/user-stats` is not written at all. In the end state a single-Subcategory session is four writes; a composite session adds one further packed-progress write per additional Subcategory touched. Firestore bills per operation, so the write count is what the schema is shaped around. See [ADR-0014](docs/adr/0014-session-stats-written-at-summary-screen.md)'s scope note for which of these is true at any given point.

**Concurrency is not addressed.** The batch is atomic but not transactional against concurrently-read state: two sessions racing on the same Subcategory (two devices, or two tabs) can both read the same pre-session `progress/details/subcategories/{subcategoryId}` document and both apply their `FieldValue.increment` deltas, double-counting `studiedCount`/`masteredCount` and XP. This is an accepted limitation for a single-account academic project, not a designed-around case — a future multi-device-concurrent design would need a transaction that re-reads that document and `progress/user-stats` at commit time.

### Study Session Summary Screen

**Route, screen and persistence all implemented.** `StudySessionSummaryRoute` is registered in the nav graph and `StudySessionSummaryScreen` displays it — both session modes route their natural end and premature exit here. `StudySessionSummaryViewModel` reconstructs the route's flattened result into a `SessionResult` and submits it, once, through `SubmitStudySessionUseCase` to the server-authoritative `submitStudySession` Cloud Function, which writes the session document, per-Subcategory progress and scoring state inside one Firestore transaction. The client itself performs no direct writes — the screen's own local reads (prior progress, scoring state) feed only the optimistic preview shown while that submission is in flight.

It is the **mandatory exit path for every session**, abandoned included, and the only place XP is computed and persisted. A freshly-finished session's result arrives as route arguments — `cardResults` flattened into parallel lists of primitives (`androidx.navigation`'s typesafe routes only derive a `NavType` for primitives, enums and lists of those, the same constraint `StudySessionRoute` already works around for its voice settings). `cardIds`/`subcategoryIds`/`states` are always present; `attemptsUsed`/`wasPreviouslyMastered` are Rated-only lists, `null` on a Fast route rather than lists of zeroes and falses. A past session instead carries only `sessionId` and is read back from `sessions/{sessionId}` — one document, `cardResults` included. Session length is capped at `StudySessionConfig.MAX_LENGTH` (50 cards), so the flattened lists stay well within the platform's navigation argument size ceiling.

Both Study Modes terminate here. Fast renders a reduced variant: time, streak, new cards and XP, with no mastered/failed counts, no mastery ring sweep and no "Study Again (Failed)".

Designed:
- **Study Again (All)** — navigates to Preview Study Session Screen with same `categoryId` + `subcategoryIds`, clearing the session stack (`popUpTo<Main>()`); card re-selection happens fresh on the Preview Study Session Screen
- **Study Again (Failed)** — shown only if ≥1 Flashcard reached Terminal State Failed; navigates directly to `RatedStudySession` with `cardIds = [failedCardIds]`, `popUpTo<Main>()`
- **Back to Home** — `popUpTo<Main>(inclusive = false)`; returns to Main on whichever tab was active. System back has the same behavior.

## Settings Screen

**Still a scratch screen** — `SettingsScreen.kt` renders `Text("Settings - NYI")` plus a Showkase entry point and sign-out; none of the sections below are built. Listed here as target design.

**Study Sessions**
- Session Flashcard count (default 20)
- Attempts per card (default 3, max 5) — persisted as `StudySessionPreferences.ratedAttempts`; the Settings *screen* row is unbuilt, but the preference itself is live and editable from the Preview screen's Attempts popup
- Partial ends the card (default off) — when on, a Partial Rating resolves the Flashcard to Terminal Partial immediately instead of re-inserting it ([ADR-0044](docs/adr/0044-three-valued-terminal-state.md)). **Not yet added** to `StudySessionPreferences`
- Default study mode (Rated | Fast) — same persisted preference the "keep as default" checkbox on the Preview Study Session Screen's Mode popup writes to (ADR-0030); also set during onboarding
- Default sort order (Default | Easiest | Hardest) — mirrors the Preview screen's Sort "keep as default" checkbox

**The "keep as default" checkboxes are fully wired** — dialog-system Gap 1 is closed. `StudySessionPreferences` (`core:domain`), `StudySessionPreferencesRepository` and `DataStoreStudySessionPreferencesLocalDataSource` all exist, and `PreviewStudySessionViewModel.onDialogConfirm` commits every checked default through `SaveStudySessionPreferenceUseCase`. All eight preferences persist: `defaultStudyMode`, `voiceAnsweringEnabled`, `ratedAttempts`, `readAloudEnabled`, `sessionLength`, `sortOrder`, `voiceSettings`, `subcategoryCountRange`. What is still missing is the Settings *screen* that would offer a second edit point.

**Voice**
- Voice settings (voice selection + playback speed — implemented, `VoiceSettingsDialog`, persisted via `DataStoreVoiceSettingsLocalDataSource`)
- Voice answering **consent** (has the user accepted the mic-permission disclosure) — implemented and persisted, `VoiceAnswerConsentRepository`/`SetVoiceAnswerConsentUseCase`. Premium-gated with real server-side entitlement enforcement (ADR-0024/0029).
- Voice answering **default-enabled** — persisted as `StudySessionPreferences.voiceAnsweringEnabled`, editable from the Preview screen's Voice answering popup
- Read-aloud/auto-play default (Fast) — persisted as `StudySessionPreferences.readAloudEnabled`. Note the session screen does not yet *act* on it — see the Fast mode entry point bug above

**Notifications**
- Daily reminder toggle (row exists; backend — FCM + WorkManager — not yet scoped/built)

**Permissions**
- Notifications, Microphone — status + link to system settings

**Daily Goal**
- Minutes/day (default 20) — also editable inline on the Progress screen; both write the same persisted value

**Account**
- Sign out

Not for Category or Subcategory management.

## Report a Problem (in-session flag icon)

Reached via the flag icon in the study session top app bar (Rated and Fast alike), enabled only while a card is displayed. Ships to production — no debug gate. Lets a user flag the current Flashcard for a specific content fix without leaving the session.

- **Dialog**: `ReportProblemDialog`, a `FlashcardsDecisionDialog`. Lists all 7 Curation Actions with icon + label ("Raise the difficulty," "Lower the difficulty," "Wrong tags," "Needs a code example," "Formatting looks broken," "Needs a full rewrite," "Duplicate or low quality") — exhaustive by a `check()` against `CurationAction.entries`, so a new action can't be added without a row. The draft always starts **empty**, regardless of the card's existing report — it never seeds from Firestore. Cancel discards the whole draft; Submit is enabled once ≥1 row is checked.
- **Writes**: on Submit — the checked Curation Actions are upserted to Firestore in one write (`SubmitCurationReportUseCase` → `CurationRepository.upsertCurationActions`). Cancel makes no write.
- **No withdrawal path**: because the draft never seeds, there is no way to un-report a previously submitted action from within the app. Withdrawal, if ever needed, is admin/sync-tooling operating on Firestore directly.
- **Resubmission is a no-op**: `DefaultCurationRepository` caches each card's last known flagged set, lazily fetched from Firestore on that card's first write; upserting a set that's already a subset of it skips the write instead of re-flagging on every reopen of the dialog.
- **No management screen**: Curation Requests are consumed only by admin sync scripts, never surfaced back to the user.

See [ADR-0017](docs/adr/0017-curation-report-system.md).

## Flashcard Cache

Two-part scheme: an always-on cache-first read policy (ADR-0038), plus a startup check that
decides when to throw the cache away (ADR-0039). Both fully built and wired.

**Cache-first reads (`DefaultFlashcardRepository`, `core:data`).** Firestore's own default read
is server-*first* — it suppresses the cached snapshot whenever it believes it is online — so
without an explicit policy, every read of a Subcategory's cards costs a network round trip, and
browse, session preview, and every filter/sort confirm each re-pay it. The repository owns a
**cache generation** (an in-memory counter, `@Singleton`-scoped, so it lives as long as the
process): the first `fetchFlashcards(subcategoryId)` for a given Subcategory in the current
generation goes to the server; every read after that in the same generation is served from the
on-device Firestore cache. An empty cache result falls through to the server rather than
surfacing as an empty success — sound only because a Subcategory always contains at least one
Flashcard (`CONTEXT.md`), so an empty cache result unambiguously means "not cached yet," never
"genuinely empty." `invalidateFlashcardCache()` bumps the generation, re-arming the server read
for every Subcategory. A server read in flight when a bump lands captures its starting generation
before the network call and only stamps that Subcategory as current if the generation is still
unchanged afterward — otherwise the response still returns to its own caller, but isn't trusted as
belonging to the new generation, so the next read of that Subcategory goes to the server again
instead of treating a pre-invalidation response as fresh.

**Seed-versioned invalidation (what calls `invalidateFlashcardCache()`).** A single Firestore
document, `meta/seed` (`{ value: <monotonic int> }`), is the freshness signal for the whole
knowledge base — one seed for everything, not one per Subcategory. `seed_firestore.py` increments
it by one as the last write of every import run, after every content write has already committed,
so a run that fails partway never bumps it. At app startup, `SyncFlashcardCacheGenerationUseCase`
(`core:domain`) reads `meta/seed` with a live (never cached) Firestore read and compares it
against a locally stored copy (`UserPreferences.localCacheSeed`, device-scoped, nullable with no
default — `null` means "never checked" and forces a mismatch on every device's first launch after
this shipped). On a mismatch, it calls `invalidateFlashcardCache()` and persists the new value. A
match, or a failure *reading* the remote seed (most commonly: offline at launch), is a true no-op —
tolerated as ordinary behavior, not an error state. A failure *persisting* the new local value is
different: the generation already bumped by that point, so it's a partial success, not a no-op —
the mismatch simply reappears and retries on the next launch. See
`SyncFlashcardCacheGenerationUseCase`'s KDoc for the full breakdown, including the local-read
failure case.

`AppStartViewModel` fires this as a decoupled side effect in `init{}` (its own
`viewModelScope.launch`, no timeout of its own — a cold-start Firestore read routinely outlasts
the 1000ms the auth check bounds itself to, and nothing here waits on the result; `viewModelScope`
cancelling the coroutine when the ViewModel clears is bound enough), never gating the auth-driven
`startupState` it exposes — a slow or absent network delays neither. See
[ADR-0038](docs/adr/0038-one-sort-order-and-flashcard-selection-seam.md) (cache-first reads,
generation seam) and
[ADR-0039](docs/adr/0039-seed-versioned-flashcard-cache-invalidation.md) (what drives the seam).

## Firestore Schema

Moved to [docs/design/firestore-schema.md](docs/design/firestore-schema.md) (2026-09-14) —
full collection/field reference and design notes live there now. This file is being split
into smaller specialized docs; see that file for the current schema.

## Flashcard Selection Algorithm

**As actually implemented** (`SelectSessionFlashcardsUseCase`, `core:domain`) — client-side, pure logic over a pool fetched per subcategory and cached in memory for the ViewModel's lifetime:

1. Filter to cards where `card.difficulty` falls in the configured range
2. Filter to cards where `card.tags` intersects the configured tag set (OR-within; step is a no-op when the set is empty)
3. Shuffle with the injected `kotlin.random.Random` (no session seed — see Preview Study Session Screen above), then take the configured length
4. Apply the configured sort order to that drawn subset only — Default keeps shuffle order, Easiest/Hardest sort by difficulty. Sorting never changes which cards were drawn (see Preview Study Session Screen above, Sort row).

**Mastery Defense (Rated only, designed — not built):** mastered Flashcards are never excluded from the session pool. Mastery Defense sets a **floor** of 10% of the session's **resolved length** — `min(configured Length, eligible pool size)`, the same shrink-on-small-pool rule Card Selection already applies above — topping a natural draw up with mastered Flashcards only when it falls short. Every mastered Flashcard in the final selection is a defence card, however it was drawn. The session is always exactly its resolved length, so the card count and estimated duration on the Preview screen stay truthful; it only equals the configured Length when the eligible pool is at least that large. Global Flashcards only; a future per-card progress filter is the only thing that removes mastered Flashcards from the pool, and when it does the floor is 0.

Session Flashcard count: user-configurable (Length row on Preview, default 20), persisted as `StudySessionPreferences.sessionLength`. The Settings-screen row that would also edit it is unbuilt.

**Previously documented here, never built, now dead:** a per-card-scored spaced-repetition selector — `(failedCount / (failedCount + correctCount)) * recencyWeight`, excluding cards where `nextReviewAt > now`. No such scoring exists anywhere in the codebase. "Not in MVP" below lists a future performance-weighted selector as the intended successor to today's random draw; the packed `progress` documents are what such a selector would read.

## Private Flashcards

Created from Subcategory Details screen via FAB. Fields:
- Question (multiline text)
- Answer (multiline text)
- Difficulty (`Slider`, 1–10, discrete steps, mandatory — no card can be saved without one, see [ADR-0010](docs/adr/0010-difficulty-field-design.md))
- Tags (multi-select from the Tags already present on this Subcategory's Flashcards, derived `distinct(card.tags)`):
  - Opens with all tags unchecked — no filter state propagated from the Subcategory Details screen
  - "General" tag is not shown; if user submits with no tags checked, "General" is auto-assigned (intentional friction against unclassified cards)
  - No "private" tag exists; the Private flag is implicit — the card lands in `users/{uid}/privateCards/{subcategoryId}/flashcards/{cardId}`, which is what surfaces it under the "Private" filter chip

Saved to `users/{uid}/privateCards/{subcategoryId}/flashcards/{cardId}`. Future: admin promotes to global pool if quality sufficient.

## Content Seeding

Two-stage Python tooling under `scripts/seed/` (Firebase Admin SDK). The intermediate
fixture is a **local, gitignored temp file — never committed**:

1. **`build_fixture.py`** — reads the curated capture inboxes (`~/.claude/flashcards/<cat>/<sub>/inbox.jsonl`),
   applies the inbox→card projection (see Import mapping above), derives `categories`/`subcategories`
   from the slugs, bin-packs each subcategory's cards into byte-budgeted `shards` (ADR-0037), and writes
   `scripts/seed/.tmp/fixture.json`. Deterministic; fails loudly on duplicate card ids, a subcategory
   slug colliding across two parent categories, or a single card too large to fit in any shard.
2. **`seed_firestore.py`** — globs `scripts/seed/.tmp/*.json` and upserts via Admin SDK.
   Categories and `shards` are always fully (re)written every run — both are pure derived/curated
   data with nothing to preserve by skipping. Subcategories default to `--skip-existing` (write if
   absent, skip if present) except `cardCount`, which is always refreshed regardless, since `shards`
   already keeps the real card count authoritative and fresh on every run. `--overwrite` forces
   subcategories too, `--dry-run` reports only. Categories land in `categories/{id}`; Subcategories
   land in `subcategories/{id}`; cards land in `subcategories/{id}/shards/{n}`. Credentials via
   `GOOGLE_APPLICATION_CREDENTIALS` (service-account JSON, gitignored).

- Supports extending existing structure without wiping *unrelated* subcategories/categories — a run only touches the subcategories present in its fixture, so third-party tools can add new subcategories incrementally. Within a subcategory a fixture *does* touch, the fixture must carry that subcategory's full card set: `shards` are fully rewritten every run (see above), so a partial fixture for an already-seeded subcategory (`--sample N`, or a third-party tool emitting only its own new cards) silently drops any existing card the fixture doesn't repeat.
- **Card `id` is a map key inside its shard's `flashcards` field** (`flashcards["<cardId>"]`), not a Firestore
  document key — a shard doc's own id is just its shard index. Card ids must still be globally unique (the
  capture skill mandates a cryptographically random hex suffix to prevent collisions — 100 historical
  collisions were repaired pre-import) since a shard doc's `flashcards` map is keyed by them.

Fixture JSON format (no `tags` section — tags are inline strings on each card):
```json
{
  "categories": [{ "id": "android", "name": "Android", "order": 0, "subcategoryCount": 1, "iconSvg": null, "color": null }],
  "subcategories": [{ "id": "android-compose", "name": "Compose", "categoryId": "android", "order": 0, "cardCount": 1 }],
  "shards": [{ "id": "0", "subcategoryId": "android-compose", "flashcards": {
    "...": { "id": "...", "question": "...", "answer": "...", "tags": ["state"], "difficulty": 4 }
  } }]
}
```

Cards are written to `subcategories/{subcategoryId}/shards/{n}`, as map entries of that shard doc's
`flashcards` field — not as their own documents (ADR-0037). The `subcategoryId` field in the fixture
drives the collection path and is not written as a document field.

## Design System

Branded Material 3 — full M3 `ColorScheme` kept intact, with an additive `BrandColors` layer for app-specific colors. See [ADR-0005](docs/adr/0005-branded-m3-design-system.md).

**Color access in composables:**
- `MaterialTheme.colorScheme.*` — M3 roles (surfaces, primary, secondary, error, etc.)
- `MaterialTheme.brandColors.*` — branded extras (gradients as `Brush`, difficulty tints, etc.)

`BrandColors` slots are added incrementally as screens need them. Do not read `Color.kt` tokens directly in composables.

## Not in MVP

- Push notifications / study reminders (needs FCM + WorkManager)
- Voice answer evaluation (backend feature, planned)
- Custom user-created tags
- Admin UI for content management
- Gamification (streaks, XP)
- Performance-weighted Quick Session (MVP uses random selection)
- Session time limit (Flashcard count override + difficulty filter are now first-class on the Preview Study Session Screen — see ADR-0030)
