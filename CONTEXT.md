# Flashcards

A mobile app for studying curated flashcards organized by subcategory, with spaced-repetition-influenced flashcard selection.

## Language

### Entities

**Category**:
A top-level knowledge domain a user can study (e.g. Android, Python).
_Avoid_: Topic, Subject, Domain

**Subcategory**:
A granular area within a Category. A Flashcard belongs to exactly one Subcategory (e.g. Compose, Coroutines, Navigation under Android). Displayed to users as **Topic** in the UI; Subcategory is the canonical term in code and internal docs. **A Subcategory always contains at least one Flashcard** — an empty Subcategory is not a state the domain admits, so "no Flashcards for this Subcategory" always means the data was not loaded rather than that there are none.
_Avoid_: Subtopic (the UI label "Topic" is intentional and not a synonym to avoid — it is the presentation-layer name)

**Tag**:
A global keyword carried by a Flashcard. One kind only — no type distinction, no internal/user-facing split; every Tag is user-facing and reusable across Subcategories (e.g. "State" may tag Flashcards in both Compose and Coroutines). Surfaced as filter chips on Subcategory Details, where the chip set is the Tags actually present on that Subcategory's Flashcards (derived, not an owned list). Tapping chips constrains both the browsed Flashcard list and the single-subcategory Study Session selection pool. Multi-select with OR semantics. A **General** Tag covers Flashcards with no narrower theme.
_Avoid_: Label, Topic, Filter (even though Tags filter, do not call the entity "Filter"); Specific Tag / Common Tag / System Tag (the typed-tag model is retired)

**Private flag**:
A derived boolean property: a Flashcard is Private iff it lives under `users/{uid}/privateCards/{subcategoryId}/flashcards/` (global cards, packed as map entries under `subcategories/{subcategoryId}/shards/` per ADR-0037, are never Private). Not a Tag. Surfaced as a "Private" filter chip on Subcategory Details (so users can study their private cards only) but absent from the New Flashcard tag selector.
_Avoid_: private Tag, System Tag

**Flashcard**:
A question-answer pair belonging to exactly one Subcategory. Part of the global admin-curated pool or a user's private collection.
_Avoid_: Card, Question

**Difficulty**:
A mandatory integer 1–10 on every Flashcard — global admin-curated and Private alike — expressing how hard the question is within its Subcategory's domain. Domain-relative: a 3 in Compose and a 3 in Coroutines both mean "a beginner in that area gets this right." Global Flashcards with no Difficulty value are filtered out at the data layer and never reach the domain (a backfill concern for the pre-existing global corpus); Private Flashcards have no such gap since the creation dialog's mandatory Slider means none can be saved without one. Used by curriculum features to order Flashcards by complexity.
_Avoid_: Score, Level, Rank

**Sort Order**:
How a set of Flashcards is ordered for the user: Default, Easiest first, or Hardest first. **One notion, not two** — the order a Subcategory's Flashcards are listed in while browsing and the order they are presented in during a Study Session are the same concept, stored once as the user's saved preference and editable from Settings, Subcategory Details and the Preview Study Session screen alike. Default means "no explicit Difficulty ordering applied", so each context falls back to its own natural order: source order when browsing, draw order inside a Session (where the shuffle has already happened). Distinct from filtering, which changes how many Flashcards are shown; Sort Order only changes their order.
_Avoid_: Sorting mode, Ordering, Sequence

**Extended Context**:
An optional supplementary payload on a global Flashcard providing self-contained teaching material — examples, code snippets, analogies, edge cases, and "why this matters" framing. Absent on simple cards (Difficulty 1–3) where the question and answer are already fully self-explanatory. Present and progressively richer as Difficulty rises: mid-range cards (4–6) include a concrete example or short snippet; hard/expert cards (7–10) include fuller context covering edge cases, cross-concept relationships, and pitfalls. Intended for a future "explain deeper" feature where an LLM re-teaches the concept from this payload. Never a duplicate of the answer field.
_Avoid_: Context, Explanation, Detail

**Private Flashcard**:
A Flashcard created by a user. Follows a submission lifecycle: `private → submitted → approved` (approved flashcards may be promoted to the global pool).
_Avoid_: User card, Custom card

**Favorite**:
A Subcategory explicitly bookmarked by a User. Displayed on the Home screen as a carousel of cards showing Subcategory and parent Category names.
_Avoid_: Starred, Saved, Liked

**Recent**:
A past Study Session surfaced on the Home screen as a carousel card. Renders as one of two variants: a **single-subcategory Recent** (session had one Subcategory — shows Subcategory + Category name, taps into Subcategory Details) or a **composite Recent** (session spanned multiple Subcategories — shows Category name only, taps into Category Details).
_Avoid_: History, Last session

**Guest**:
A transient, pre-link Firebase Anonymous Auth session — never a standing alternate identity, never a
permanent or optional mode. Started only at the moment onboarding's final step is committed, and only
if the user picked at least one Favorite, so a uid exists to write that pick under before real sign-in.
Always resolves into a real **User** via `linkWithCredential` at Login, which keeps the same uid and
everything written under it (e.g. the Favorite); on a linking collision (the credential is already
tied to a different existing User — a returning user on a new device) the session and anything written
under it are discarded outright, not merged. Sign-in stays mandatory: nothing reachable past onboarding
treats a Guest as authenticated.
_Avoid_: Anonymous user, Temp user

**User**:
An authenticated person using the app. Represented in code as `AuthUser` with `uid`, `email`, `displayName`, `photoUrl`.
_Avoid_: Account, Player, Learner

**Study Session**:
A focused learning instance scoped to one or more Subcategories within a single Category. Has exactly one **Study Mode**. Every Study Session records **Card Progress** for the eligible (non-Private) Flashcards it actually puts in front of the User; only a Rated Study Session records Ratings, Attempts and Terminal States. A session with exactly one Subcategory is a **single-subcategory session**; a session spanning multiple Subcategories is a **composite session** — the umbrella term, true of a Quick Session and a Custom Session alike. Composite is not itself an entry point: every Composite session is either Quick (system-selected) or Custom (user-selected) — see **Study Creation**.
_Avoid_: Quiz, Session alone (ambiguous with auth session)

**Session Result**:
The domain type describing what happened in one Study Session, of either Study Mode: session identity, Study Mode, start timestamp, duration, whether the session was abandoned before the deck was finished, denormalized Category/Subcategory ids and names, the shared `newCardsStudied` count, its local calendar `studyDate` and the Daily Goal value in effect when it ended ([ADR-0048](docs/adr/0048-streak-and-daily-goal-ride-the-session-payload.md)), and the per-Flashcard `cardResults` map (see **Flashcard Result**). A Rated Session Result additionally carries the Mastered/Partial/Defended/De-mastered counts — a Fast one has no Terminal States to tally, so those fields are absent, not zero. Sealed once, at session termination — it carries no XP *totals*, only the config/context needed to compute them.
**The persisted document is a distinct, larger shape**, not this type verbatim: the server-authoritative `submitStudySession` Cloud Function ([ADR-0014](docs/adr/0014-session-stats-written-at-summary-screen.md)) builds it from the submitted Session Result plus the computed `xpBreakdown`/`xpTotal` and streak/daily-goal awards ([ADR-0047](docs/adr/0047-xp-values-behind-a-config-repository.md), [ADR-0048](docs/adr/0048-streak-and-daily-goal-ride-the-session-payload.md)), and that document is what's written once, inside one Firestore transaction — nothing is written while a session runs, and the client no longer writes any of it directly. Stored at `users/{uid}/sessions/{sessionId}`, with `studyMode` as the one field a reader needs to know which shape the rest of the document is in.
_Avoid_: Ledger (retired name), Outcome, Session outcome, Session record

**Flashcard Result**:
One entry in a Session Result's `cardResults` map: a Flashcard's Subcategory id and its Card Progress state. A Rated entry's state is one of `Failed`/`Partial`/`Mastered` (its Terminal State mapped across), plus the Attempts used and whether it was previously Mastered; a Fast entry's state is always `Seen`, with no Attempts and no mastery fields — Fast has neither concept, so those fields are absent from a Fast entry rather than written as `0`/`false`. Which shape an entry has is read off the Session Result's own `studyMode`, never a second per-entry tag. Never carries a transcript.
**Its `state` is this session's own recorded outcome, not the same thing as the card's persisted Card Progress.** A previously-Mastered card ending Partial has `state == Partial` here — Partial is mastery-neutral, so the stored Card Progress for that same card stays `Mastered`. Merging a Flashcard Result into Card Progress is a separate step with its own rules (see **Card Progress**), never a direct overwrite of one by the other, even though both use the same four-valued type.
_Avoid_: Outcome, Ledger entry (retired names), Card record (that name belongs to `RatedSessionCardRecord`, the in-flight per-card state the Rated state machine keeps *during* play — a different type at a different layer)

**Study Mode**:
The interaction mechanic of a Study Session. Two values:
- **Rated**: user reveals each answer manually, then self-rates (Failed / Partial / Correct) — or chooses **Voice Answering** up front for hands-free listen-and-grade instead. Each Flashcard accumulates Attempts until it reaches a **Terminal State**.
- **Fast**: cards advance question → reveal answer → next, either manually (tap to reveal, tap to advance) or with read-aloud enabled — system TTS reads the question aloud, pauses, reads the answer aloud, then auto-advances hands-free. User controls playback via transport controls (pause / play / skip / speed slider) when read-aloud is on. Playback continues with the screen off or app backgrounded. No Ratings, no Attempts, no Terminal States — a Fast Study Session's only per-card record is that a Flashcard became **Studied**.
_Avoid_: Automatic mode, Passive mode, Browse mode, Voice mode (voice is the delivery mechanism, not the mode name)

**Attempt**:
A single presentation of a Flashcard to the user within a **Rated** Study Session, completed when the user submits a **Rating**. Each Flashcard has a maximum number of Attempts per session, user-configurable in Settings (default 3, max 5). A Voice Answering silence timeout consumes no Attempt. Does not apply to Fast Study Sessions.
_Avoid_: Turn, Round, Try

**Rating**:
The user's self-assessment after viewing an answer in a **Rated** Study Session. Values: **Failed**, **Partial**, **Correct**. Produced either by a manual self-rating tap or by **Voice Answering**'s automatic grade — both write identically. A Correct Rating ends the Flashcard's Attempts immediately. A Failed or Partial Rating re-inserts the Flashcard into the session queue at a bounded random distance ahead — 2–4 cards for Failed, 5–9 for Partial, so weaker recall returns sooner ([ADR-0046](docs/adr/0046-failed-and-partial-re-insertion-placement.md)) — unless its Attempts are exhausted, or unless the user has turned on the setting that makes a Partial Rating end the Flashcard on the spot. Ratings accumulate: a Flashcard's **Terminal State** is decided by the best Rating it ever achieved, not the last one. Does not apply to Fast Study Sessions.
_Avoid_: Score, Grade, Answer, Response

**Voice Answering**:
A Rated-Study-Sessions-only mechanic that replaces manual reveal-and-self-rate with hands-free listen-transcribe-grade: the shared Fast-mode TTS engine reads the question, the app listens for a spoken answer, transcribes and grades it, and the resulting grade band becomes the Flashcard's **Rating** exactly as a manual tap would. Off by default. Selectable up front as a row on the **Preview Study Session Screen** (ADR-0030) — this is a decision made once at session entry, with no way to change it once the session has started. Enabling it auto-enables question TTS through the same engine Fast mode uses, but stops after the question — it never auto-progresses to reading the answer. The microphone is requested on the Preview Study Session Screen's Start tap, preceded once ever by a privacy info notice.
_Avoid_: Voice mode (voice is the delivery mechanism, not a Study Mode — see Study Mode's avoid list), Voice grading (grading is the mechanism inside the feature, not the feature's name)

**Terminal State**:
A Flashcard's final outcome in a **Rated** Study Session, decided by the best **Rating** it achieved across all its Attempts. Three values: **Mastered** (Correct on any Attempt), **Partial** (never Correct, but Partial at least once), **Failed** (never better than Failed). A Flashcard reaches a Terminal State when it is rated Correct, when it exhausts its Attempts limit, or when a Partial Rating ends it under the user's re-queueing setting. Does not apply to Fast Study Sessions. See [ADR-0044](docs/adr/0044-three-valued-terminal-state.md).
_Avoid_: Final state, End state, Result

**Mastered**:
The Terminal State of a Flashcard that received a Correct Rating within a **Rated** Study Session. Also the **Card Progress** state that Terminal State writes, and the only state that counts toward **Persistent Mastery**.
_Avoid_: Completed, Passed, Correct (Correct is the Rating that causes Mastered, not a synonym)

**Partial**:
The Terminal State of a Flashcard that was never rated Correct but was rated Partial at least once within a **Rated** Study Session. Earns a small positive **XP** award and is mastery-neutral: it never grants Persistent Mastery, and a **Mastery Defense** card ending Partial keeps the mastery it was defending rather than losing it.
_Avoid_: Half-mastered, Partially correct (that is the Rating, not the outcome), Incomplete

**Card Progress**:
A User's per-Flashcard record of everything they have ever done with that Flashcard, held as one entry in a packed per-Subcategory progress document. Carries a single **state** — `Seen`, `Failed`, `Partial` or `Mastered` — plus when the Flashcard was first studied and when it was last mastered. Written by both Study Modes: a Rated Study Session writes the Flashcard's **Terminal State**, a Fast Study Session writes `Seen` and only when no record exists yet, so re-listening can never downgrade a Mastered Flashcard. **Private Flashcards never receive a Card Progress record.** See [ADR-0016](docs/adr/0016-card-progress-model.md).
_Avoid_: Card state, Review record, Progress entry

**Studied**:
The set of Flashcards a User holds any **Card Progress** for — that is, every Flashcard they have ever completed an Attempt on in a Rated Study Session, or seen the answer to in a Fast Study Session. Being drawn into a Study Session is not enough; the Flashcard must have actually reached the User. Monotonic: a Flashcard never leaves the Studied set. **Mastered** is always a subset of Studied, which is why the two can be shown as alternate perspectives on the same progress ring — a coverage measure and an ownership measure over the same denominator.
_Avoid_: Seen (that is one Card Progress state, not the set), Attempted, Reviewed, Touched

**Curation Request**:
A user-submitted signal that a global Flashcard needs a specific content fix, raised via the in-session flag icon's **"Report a problem"** dialog (Rated and Fast alike). Stored at `users/{uid}/curationRequests/{cardId}`. The dialog's draft always starts empty — it never seeds from the card's existing report — so Submit is purely additive: it upserts whichever Curation Actions are checked into the stored map, one write, in a single call. No suppression — a flagged Flashcard still appears in Study Sessions. **No in-app withdrawal path exists**: there is no management screen, and unchecking a row no longer removes it from a previous submission (the draft that row belonged to is gone) — withdrawal is admin/sync-tooling-only. Consumed by admin sync scripts. See [ADR-0017](docs/adr/0017-curation-report-system.md).
_Avoid_: Flag, Flag Action, Curation Flag

**Curation Action**:
A specific fix directive attached to a Curation Request. Values: `DifficultyTooEasy` (raise difficulty), `DifficultyTooHard` (lower difficulty), `WrongTags` (tags don't fit the card), `NeedsCodeExample` (answer needs a code block), `BacktickRedo` (inline-code formatting is wrong), `FullRedo` (factually wrong or structurally broken), `Delete` (card is duplicate or worthless). `DifficultyTooEasy` and `DifficultyTooHard` are mutually exclusive; all other actions can coexist. Every value gets a row in the Report a Problem dialog — enforced at compile-by-check time against `CurationAction.entries`, so a new action cannot be added without a row. Presented to the user as: "Raise the difficulty," "Lower the difficulty," "Wrong tags," "Needs a code example," "Formatting looks broken," "Needs a full rewrite," "Duplicate or low quality."
_Avoid_: Flag Action, Curation Type, Curation Flag Action

### Activities

**Study Creation**:
The flow a user goes through to start a Study Session. All entry points route through the **Preview Study Session Screen** before the session begins.
- **Single-subcategory**: tap a Subcategory's **play button** on Category Details (or "Start session" in the bottom toolbar of Subcategory Details) → Preview Study Session Screen → session begins. Tapping the Subcategory row itself opens **Subcategory Details** and starts nothing — the row browses, the play button studies ([ADR-0041](docs/adr/0041-topic-row-tap-browses-play-button-studies.md)).
- **Quick Session**: tap "Quick Session" on Category Details → system samples a random count of Subcategories — bounded by the user's `subcategoryCountRange` preference — then randomly selects that many Subcategories and draws Flashcards from them → Preview Study Session Screen → session begins. Re-randomize re-rolls the Subcategory sample itself, not just the card draw.
- **Custom**: enter **Selection Mode** on Category Details (bottom-toolbar toggle, or long-press any Subcategory row) → user manually chooses every Subcategory that enters the session → taps "Custom session" → Preview Study Session Screen → session begins. A Composite session when multiple Subcategories are selected (see **Study Session**) — a single selection makes it a single-subcategory session instead. Not every Composite session is Custom — Quick is the other way in.
_Avoid_: Composite Session (retired name for this entry point; Composite itself survives as the broader structural term)
_Avoid_: "Start Custom Session" (retired label — never built; the entry point is the Selection Mode toggle, and the CTA it reveals reads "Custom session")
_Avoid_: Session setup, Session wizard

**Selection Mode**:
The second mode of the Category Details screen, in which its Subcategory list becomes a multi-select instead of a set of navigation rows: each row swaps its play button and chevron for a checkbox, a selected row tints, and the bottom toolbar's CTA becomes the **Custom** Study Creation entry point. Entered by the bottom-toolbar toggle or by long-pressing any Subcategory row (which also selects that row); left by that same toggle, the top app bar's back arrow, or system back. A selection is a transient gesture, not a saved document: leaving Selection Mode discards it, and it does not survive process death. No other screen has a Selection Mode.
_Avoid_: Multi-select mode, Edit mode, Selection state

**Browse Search**:
The inline search flow on the Browse screen: a query typed into the search box live-queries Subcategories by name prefix and locally prefix-matches loaded Categories, rendering matches as two sections — Subcategories (labelled **Topic** per the Subcategory UI name) above Categories. Not a separate route; takes no back-stack entry. See [docs/design/category-search.md](docs/design/category-search.md).
_Avoid_: Category search, Topic search (Subcategory is the canonical term outside the UI label; see Subcategory's avoid list)

**Preview Study Session Screen**:
A full-screen preview shown before every Study Session begins. A read-only hero shows session scope (card count, topic count, estimated duration). Below it, a plain column presents each adjustable session setting as a summary row — Mode (Rated | Fast), Voice answering (Rated only), Length, Filters, Sort — each showing its current value and opening a focused **dialog**; plus a "Start session" button and a "Re-randomize" button (multi-topic and Quick sessions only). Only place in the app where Study Mode (and, up front, Voice answering) is chosen for a concrete session — onboarding and the Settings screen only set the persisted default, they don't start a session. Each Preview setting popup (except Filters) also carries a "keep as default" checkbox to update that persisted default from here. It also owns the microphone handshake for Voice Answering: a one-time privacy info dialog, then the microphone request, both on the Start tap. A permanently denied microphone replaces the hero with an empty state offering Open Settings and a Switch to Manual that applies to this session only, while Start stays enabled and asks again (a refusal that stays silent shows a snackbar pointing to system settings) — the Study Mode and Voice answering choice never change on their own. ADR-0030 additionally specifies a persistent no-scrim bottom-sheet chrome around these rows and a mode-dependent Voice/TTS row — both designed, not yet built.
_Avoid_: Pre-start Screen (retired name), Pre-session screen, Session config, Mode picker

**Persistent Mastery**:
The set of Flashcards a User currently holds mastery on: those whose **Card Progress** state is `Mastered`. Mastery is removed (de-mastered) when a Flashcard reaches a **Failed** Terminal State in a subsequent Rated Study Session — a **Partial** Terminal State leaves it intact. De-mastery moves the Card Progress state down; it never deletes the record, so the Flashcard stays **Studied**. Applies to global Flashcards only; Private Flashcards are excluded.
_Avoid_: Permanent mastery, Long-term mastery, Mastered set

**Mastery Defense**:
The mechanic that guarantees a Rated Study Session contains some previously mastered Flashcards, so that **Persistent Mastery** is periodically re-tested rather than assumed. Mastered Flashcards are never excluded from the session pool; Mastery Defense sets a **floor** of 10% of the session's resolved length (`min(configured Length, eligible pool size)`), topping the draw up only when a natural draw falls short. The session is always exactly its resolved length, so the card count and estimated duration shown on the Preview Study Session Screen stay truthful — it only equals the configured Length when the eligible pool is at least that large. Every mastered Flashcard in a session is a Mastery Defense card, however it was drawn. A Mastery Defense card is visually marked with a shield icon during the session. Successfully answering one (Correct on any Attempt) retains Persistent Mastery and earns bonus XP. A **Failed** Terminal State removes the card from Persistent Mastery (de-mastery) and costs XP; a **Partial** Terminal State is neutral — mastery is retained, and neither the bonus nor the penalty applies. Mastery Defense applies to Rated sessions only, and to global Flashcards only.
_Avoid_: Review card, Recall challenge

**XP (Experience Points)**:
Points earned by a User through study activities: studying a Flashcard for the first time, card mastery, a Partial Terminal State, defending mastery, time studied, session completion, daily goal and streak. Accumulate toward the next Level. XP can decrease when a previously mastered Flashcard is de-mastered (XP loss), but Level never decreases — XP loss is clamped to the current Level's floor. Card-level XP is never earned from **Private Flashcards**. Every XP amount is configuration rather than a fixed rule of the domain ([ADR-0047](docs/adr/0047-xp-values-behind-a-config-repository.md)), and a Study Session is scored against the configuration in force when it began.
_Avoid_: Score, Points, Credits

**Level**:
A User's progression milestone derived from total XP accumulated. Early levels are fast to achieve; later levels require significantly more XP (super-linear curve). Level-up triggers a celebration animation. Milestone levels (5, 10, 25, 50, 100) unlock badges. Level is displayed on the Progress screen and in the medium launcher widget.
_Avoid_: Rank, Tier, Grade

**Streak**:
The count of consecutive calendar days on which a User reached the Session Summary screen for at least one Study Session (partial or full) — the same deferred-commit boundary every other session write uses, so a session that never reaches Summary never counts. Day boundary is midnight in the device's local timezone, computed client-side and submitted with each session as `studyDate` ([ADR-0048](docs/adr/0048-streak-and-daily-goal-ride-the-session-payload.md)) — the server has no other way to learn a device's local calendar day. Evaluated and written server-side, inside `submitStudySession`'s own transaction; a submission whose day is not later than the stored one never advances or regresses it. A Streak breaks when a full calendar day passes without a counted session. Best Streak is the historical peak; it never decrements.
_Avoid_: Combo, Daily count

**Daily Goal**:
A User-configured target for minutes studied per calendar day. Default: 20 minutes. Set during onboarding (skippable) and editable inline on the Progress screen. Lives only in local device preferences, never synced to Firestore ([ADR-0048](docs/adr/0048-streak-and-daily-goal-ride-the-session-payload.md)); each session submits its own `dailyGoalMinutes`, captured at session end. Meeting it awards XP once per calendar day, evaluated **live** against whichever goal value the triggering submission carried — not day-locked, so editing the goal mid-day changes what the next submitted session is judged against, immediately.
_Avoid_: Study target, Quota

**Sessions Completed**:
Count of Study Sessions in which the User reached deck end (last card completed). Abandoned sessions (exited before deck end) do not count. Tracked as a lifetime aggregate stat on the Progress screen.
_Avoid_: Sessions finished, Sessions done


## Relationships

- A **Category** contains one or more **Subcategories**
- A **Flashcard** belongs to exactly one **Subcategory**
- A **Flashcard** carries one or more **Tags** (all global, all user-facing) and a **Private flag**; the same Tag may appear on Flashcards across different Subcategories
- A **Study Session** draws **Flashcards** from one or more **Subcategories** within a single **Category**
- A **Recent** is a past **Study Session** — single-subcategory if one Subcategory, composite if multiple
- A **Favorite** is a bookmarked **Subcategory**
- An **Attempt** produces exactly one **Rating** *(Rated sessions only)*
- **Voice Answering** is chosen up front for a **Rated** Study Session and fixed for its duration; its automatic grade produces a **Rating** the same way a manual tap does
- A **Flashcard** in a **Rated** Study Session has at most as many **Attempts** as the User's configured limit (default 3, max 5)
- A **Terminal State** is decided by the best **Rating** a Flashcard achieved: Correct → Mastered, else Partial → Partial, else Failed *(Rated sessions only)*
- A **Flashcard** the User has any **Card Progress** for is **Studied**; a Flashcard whose Card Progress state is `Mastered` is in **Persistent Mastery** — so Persistent Mastery is a subset of Studied
- A **Study Session** of either Study Mode adds Flashcards to the **Studied** set; only a **Rated** one changes **Persistent Mastery**
- A **Private Flashcard** has no **Card Progress**, is never **Studied**, and never earns card-level **XP**
- A **Flashcard** in **Persistent Mastery** is eligible to appear as a **Mastery Defense** card in future Rated Study Sessions
- A **User** accumulates **XP** through study activity; XP determines **Level**
- A **Streak** belongs to a **User** and increments once per calendar day a Study Session reaches the Session Summary screen
- A **Daily Goal** belongs to a **User** and is compared against today's total studied minutes

