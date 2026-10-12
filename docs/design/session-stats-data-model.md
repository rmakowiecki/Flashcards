# Session Stats & Data Model

## Write point

All session statistics are written to Firestore **once, at the Session Summary screen**, as a single atomic batch. No incremental writes occur during a session — not per card, not per rating, not per voice grade. This keeps the write path simple, scales without hot-document contention, and means a session is either fully recorded or not recorded at all. See [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md).

The batch contains, in one commit:

- the session document, with its per-card `cardResults` embedded — sealed by `studyMode`: a Rated
  document carries the mastery counters and full `cardResults` entries, a Fast document has none of
  the Rated-only fields at all, not zeroed ones
- one packed `progress/details/subcategories/{subcategoryId}` document per Subcategory the session touched
- `users/{uid}/progress/summary` counter increments
- `users/{uid}/progress/user-stats` — the scoring state

A single-Subcategory session is therefore **four writes**; a composite session adds one further
`progress` write per additional Subcategory touched — three fixed writes plus one per Subcategory.

## Session completion vs abandoned sessions

**Full session:** User reaches deck end. Session Summary is shown automatically.

**Abandoned session:** User presses back or taps the X button mid-session. A confirmation dialog is shown. On confirmation the user is taken to the Session Summary screen, which triggers the write with everything accumulated up to that point. A queued card the user never reached is absent from `cardResults`; a queued card that already completed at least one Attempt is force-resolved into `cardResults` using its best rating so far, the same rule natural resolution uses — it already satisfies Studied, so exit does not discard it.

Abandoned sessions:
- Count toward streak (the session reached the Summary screen)
- Count toward daily goal (time studied is recorded)
- Count toward time-based stats (total time, history chart, per-category breakdown)
- Record card progress for every card that actually reached the user
- Do **not** count toward "Sessions Completed" (deck end was not reached)
- Do **not** earn session-completion XP
- Are flagged `isAbandoned: true`

("Abandoned," not "partial" — `Partial` already names a Rated Terminal State, and calling an early exit a "partial session" invited exactly that confusion.)

There is no way to exit a Study Session without passing through the Session Summary screen. An app kill or crash is the exception, and records nothing at all — not an abandoned session, but no session.

## Session duration

Duration = first card shown → deck end or exit confirmation. The clock starts when a card is actually on screen, not when the route is entered, so a session whose card load fails banks nothing.

v1 is deliberately simplistic: the clock runs unconditional of backgrounding or playback state — a session backgrounded for a minute banks that minute regardless of mode. Revisit with a pause/resume-on-background policy if this proves to matter in practice. Time on the Session Summary screen is excluded (the clock stops at termination, before Summary is shown).

## Streak rules

- **Definition of a study day:** any session that reaches the Session Summary screen (partial or full)
- **Day boundary:** midnight in the device's local timezone, applied to the session's **start** timestamp — so a session crossing midnight counts toward the day it began
- **Streak increments** when a session reaches Summary on a calendar day later than `lastStudyDate`
- **Streak breaks** when a full calendar day passes with no session
- **Best streak** is the historical maximum; never decremented

## Daily goal

- Unit: minutes studied per day
- Default: 20 minutes
- Set during onboarding (skippable); editable inline on the Progress screen
- Goal met = today's total studied minutes ≥ daily goal minutes
- XP awarded once per calendar day, gated on `goalMetDate`, when the goal is first met

## Firestore schema

### Session Result: `users/{uid}/sessions/{sessionId}`

**One session is one document.** Aggregates, denormalized names, and the per-card results embedded as a `cardResults` map. Home does not query this collection: its Recents read the server-written `recents/state` projection ([ADR-0057](../adr/0057-recents-state-projection.md)).

**The document is sealed by its own `studyMode`.** Rated has Ratings, Attempts and Terminal States; Fast has none of that, only `Seen`. Rather than a Fast document carrying zeroed mastery counters, the four Rated-only fields below are **absent** on a Fast document — not written as 0. A reader decides which fields to expect from `studyMode` alone.

| Field | Type | Notes |
|---|---|---|
| `sessionId` | String | Auto-generated |
| `startTimestamp` | Timestamp | UTC; day attribution uses device local time |
| `durationSeconds` | Int | See [Session duration](#session-duration) |
| `studyMode` | Enum | `FAST` or `RATED` — also the discriminant for which of the fields below exist |
| `isAbandoned` | Boolean | `true` if the user exited before deck end |
| `categoryId` | String | |
| `categoryName` | String | Denormalized — Recents renders it without a join |
| `subcategoryIds` | List\<String\> | One or more |
| `subcategoryNames` | List\<String\> | Denormalized, same reason |
| `cardCount` | Int | Distinct Flashcards the session put in front of the user |
| `newCardsStudied` | Int | **Both modes** — Flashcards entering **Studied** for the first time; not a mastery concept |
| `cardsMastered` | Int | **Rated only, absent on Fast** — cards entering mastery **this session**, not cards that were already mastered |
| `cardsPartial` | Int | **Rated only, absent on Fast** |
| `cardsDefended` | Int | **Rated only, absent on Fast** — mastery held under Mastery Defense — **disjoint from** `cardsMastered`: a card that was already mastered and stays mastered counts here, not there |
| `cardsDemastered` | Int | **Rated only, absent on Fast** — mastery lost |
| `xpBreakdown` | Map | Computed XP per category — see below |
| `xpTotal` | Long | Sum of every entry in `xpBreakdown`; what the Summary's XP pour animates to |

The four Rated-only counts exist so `xpBreakdown` is auditable against them, not so the breakdown has to be recomputed from them.

#### `xpBreakdown`: the computed award, not the config that produced it

| Field | Type | Notes |
|---|---|---|
| `newCards` | Long | **Both modes** — `newCardsStudied × XpConfig.newCardXp` at the time of this session |
| `mastered` | Long | **Rated only, absent on Fast** — `cardsMastered × XpConfig.cardMasteredXp` |
| `partial` | Long | **Rated only, absent on Fast** — `cardsPartial × XpConfig.cardPartialXp` |
| `masteryDefenseBonus` | Long | **Rated only, absent on Fast** — `cardsDefended × XpConfig.cardDefendedXp` |
| `demastered` | Long | **Rated only, absent on Fast** — `cardsDemastered × XpConfig.cardDemasteredXp` (negative) |
| `timeStudied` | Long | Minutes studied × `XpConfig.xpPerMinute` |
| `dailyGoalBonus` | Long | 0 unless this session is the one that met the calendar day's goal |
| `sessionCompletionBonus` | Long | 0 for abandoned sessions |
| `streakBonus` | Long | 0 unless this session extended the streak |

The four Rated-only sub-fields follow the same rule as their source counters: absent on a Fast session's `xpBreakdown`, not present at 0. `xpTotal` sums whichever sub-fields actually exist.

Each field is the **already-computed XP amount**, not a multiplier or a config reference. This is what makes a past Summary reproducible after `XpConfig` changes: the Summary screen renders `xpBreakdown` directly rather than re-deriving it from the outcome counts and the *current* config, so a later config change can never alter what an old session displays. `XpConfig`/`XpConfigRepository` are local and hardcoded today ([ADR-0047](../adr/0047-xp-values-behind-a-config-repository.md)); nothing here depends on that seam existing yet.

#### The embedded results: `cardResults`

A map on the session document, keyed by card id, holding one entry per card the session actually recorded. Each entry is sealed by the same `studyMode` as its parent document — a Fast entry is not a Rated entry with its Rated-only fields zeroed, it simply doesn't have them.

| Field | Type | Notes |
|---|---|---|
| `subcategoryId` | String | Which topic the card belongs to |
| `state` | Enum | `Mastered` \| `Partial` \| `Failed` (Rated) or `Seen` (Fast — the only value a Fast entry can have) |
| `attemptsUsed` | Int | **Rated entries only, absent on Fast** |
| `wasPreviouslyMastered` | Boolean | **Rated entries only, absent on Fast** — whether this was a Mastery Defense card |

No transcript field exists here. A voice-answered card's sanitized transcript is shown on screen
transiently, during the Rated session, to display the grading feedback — it is never persisted, on
this document or anywhere else. See [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md).

It is embedded rather than held in a subcollection because Firestore bills per document read: Recents pays the same read count either way, so splitting bought no read saving while costing a write per card. Size is bounded — one entry per distinct card, capped by the maximum session length — to a handful of scalar fields each. See ADR-0014.

Recents transfers this map without rendering it, since the Android client SDK has no field projection. Revisit only if measured.

### Scoring state: `users/{uid}/progress/user-stats`

| Field | Type | Notes |
|---|---|---|
| `xp` | Long | Total XP currently held |
| `level` | Int | Current level (denormalized); never decreases |
| `xpIntoCurrentLevel` | Long | XP within current level (for the progress bar); never negative |
| `currentStreak` | Int | Current streak in days |
| `bestStreak` | Int | Historical peak streak; never decreases |
| `lastStudyDate` | String | `yyyy-MM-dd` local calendar date of the last streak-counted session |
| `goalMetDate` | String | `yyyy-MM-dd` local calendar date the daily goal was last met |

The two dates are strings rather than Timestamps: they are calendar days in the device's local zone, not instants, and the only questions asked of them are same-day and later-day. String comparison on an ISO date answers both without a timezone-shift hazard.

**`dailyGoalMinutes` is not here.** It lives in local preferences alongside the other device-scoped settings, where Settings already writes it. A Firestore copy would be a second writable source with no sync story. The cost is that the goal does not follow a user to a new device — the same trade-off `hasSeenOnboarding` already makes explicitly.

**Scoring state is not on `users/{uid}` itself.** Entitlement is not a field on that document — it is the separate subcollection `users/{uid}/entitlement/premium` (`functions/src/lib/entitlement.ts`), written only by the Admin SDK and read server-side by the premium Cloud Function; that subcollection stays default-denied regardless of any rule on the parent document. Scoring state lives under `progress/` instead simply to keep `users/{uid}` reserved for identity and admin-managed data. See [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md).

### Card progress: `users/{uid}/progress/details/subcategories/{subcategoryId}` and `users/{uid}/progress/summary`

See [Card Progress and Persistent Mastery](persistent-card-mastery.md).

## DataStore cache (widget and offline)

Aggregates are pre-computed on each session end and written to DataStore for fast local reads (widget, offline Progress screen).

| Key | Value | Updated |
|---|---|---|
| `currentStreak` | Int | Each session end |
| `bestStreak` | Int | Each session end |
| `todayMinutes` | Int | Each session end |
| `weeklyMinutes` | Int | Each session end |
| `dailyGoalMinutes` | Int | Each goal change or session end |
| `level` | Int | Each session end |
| `xpIntoCurrentLevel` | Long | Each session end |
| `xpForNextLevel` | Long | Each session end |
| `totalMinutes` | Long | Each session end |
| `totalSessionsCompleted` | Int | Each full-session end |
| `totalCardsMastered` | Int | Each session end |
| `totalCardsStudied` | Int | Each session end |

`totalCardsMastered` mirrors the live Persistent Mastery set and therefore **decreases on de-mastery**, matching the progress summary's mastered counts. `totalCardsStudied` is monotonic. Neither is a lifetime "ever mastered" tally.

History chart data (7/30 days) and per-category breakdown are **not** cached — they need per-day, per-category resolution that does not reduce to a flat key-value set, and are recomputed from Firestore when the Progress screen loads.

## Cross-device sync / reinstall

On fresh install or reinstall:

1. Read `users/{uid}/progress/user-stats` for `xp`, `level`, `xpIntoCurrentLevel`, `currentStreak`, `bestStreak`, `lastStudyDate`, `goalMetDate` — these are authoritative and are **not** replayed from history. `dailyGoalMinutes` is device-scoped local state and resets to its default on reinstall
2. Fetch `users/{uid}/sessions` to recompute the time-windowed aggregates that genuinely need history (`todayMinutes`, `weeklyMinutes`, `totalMinutes`, `totalSessionsCompleted`)
3. Read `progress/summary` — one document — to recompute `totalCardsMastered` and `totalCardsStudied`
4. Write aggregates to DataStore
5. Show Progress screen

XP, level and streak are stored as computed scalars precisely so they never have to be re-derived by replaying the XP formula over session history — which would also give the wrong answer if the XP config had changed in the interim.

**Accepted future edge case:** step 2 fetches the *entire* `sessions` collection, even though only the `today`/`week` windows need raw records — `totalMinutes` and `totalSessionsCompleted` could be bounded, running counters instead. For a single-account academic project this is not worth building now; flagged here so it isn't mistaken for an oversight if it ever needs revisiting — a user with years of history reinstalling would pay a read per historical session, which racks up Firestore read-operation cost with account age.
