# XP & Leveling System

## Purpose

RPG-style progression layer to improve user retention. XP is earned by studying and mastering Flashcards. Level reflects cumulative effort. The system naturally incentivises Rated sessions over Fast sessions without making Fast sessions feel pointless.

## XP sources

| Source | Amount | Notes |
|---|---|---|
| New Flashcard studied | **10 XP per card** (flat) | Both modes. Awarded once ever per Flashcard, the first time it enters **Studied**. Never for Private Flashcards. |
| Card Mastered (Rated) | **100 XP per card** (flat) | Rated only. Flat rate regardless of difficulty. |
| Card Partial (Rated) | **25 XP per card** (flat) | Rated only. Terminal State Partial — never Correct, but Partial at least once. |
| Mastery Defended (Correct on any Attempt) | **50 XP per card** (flat) | Rated only. See [Card Progress and Persistent Mastery](persistent-card-mastery.md). |
| Card de-mastered (Failed Terminal State on a previously mastered card) | **−80 XP per card** (flat) | Rated only. A **Partial** Terminal State on a defended card is neutral — no bonus, no penalty. |
| Session fully completed (deck end reached) | **+500 XP** | Both modes. Abandoned sessions do not earn this. |
| Daily goal met | **+1000 XP** | Awarded once per calendar day, when today's studied minutes first reach the goal. |
| Streak continuation | `min(streakDays × 250, 2500)` XP | Day 1 = 250, Day 2 = 500, …, Day 10+ = 2500 (cap). Once per calendar day on which a session reaches the Summary screen. |
| Time studied | **10 XP per minute** | Both modes. Session duration, first card shown → deck end or exit. |

> **These numbers are configuration, not domain rules.** Every value above, and the level-curve parameters below, live in `XpConfig` behind `XpConfigRepository` — see [ADR-0047](../adr/0047-xp-values-behind-a-config-repository.md). The implementation is local and hardcoded today; the seam exists so the balance can be tuned without shipping a release. A session is scored against the config captured when it **started**, so a config change can never retroactively rewrite a session already in progress.

Flat per-card rates keep the session summary equation readable: `{N} cards × {rate} = {XP}`.

## Fast vs Rated XP

Fast sessions earn: new-card XP, time XP, session completion XP, streak XP, daily goal XP.

Fast sessions do not earn: card mastery XP, Partial XP, mastery defense XP, and cannot suffer de-mastery XP loss.

A Fast-only user therefore still sees their coverage and their XP grow — just more slowly than a Rated user, and without ever touching Persistent Mastery.

## Level curve

Shape: super-linear (fast early levels, increasingly expensive later ones). Constraints:

- XP required per level increases with level number (not constant)
- Early levels reachable within 1–2 good sessions
- Mid-range levels (10–25) require consistent multi-day effort
- High levels (50+) are long-term achievements
- All per-level XP thresholds rounded up to the nearest 1000 XP

Formula: `ceil(base × level^exponent / 1000) × 1000`, where `base` and `exponent` are `XpConfig` values rather than constants — so hitting the milestone targets above is a tuning exercise, not a code change.

## Level-up rewards

- Confetti/celebration animation on the Progress screen, and on the Session Summary screen when the level-up happened during that session (the bar fills, resets and pulses the Level number for every Level crossed)
- Milestone badges unlocked at levels 5, 10, 25, 50, 100 — displayed on the Progress screen
- Badge/achievement system detail: deferred to a separate design session
- No XP burst on level-up (keeps the curve clean)

## Session Summary XP presentation

XP is calculated **on the Session Summary screen**, from the session result and the `XpConfig` snapshot the session carried, and written to Firestore there as the `xpBreakdown` map on the session document ([Session Stats & Data Model](session-stats-data-model.md)), as part of the single session-commit batch ([ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md)). The screen shows the total with an info button that opens the itemized breakdown in a dialog: one row per source (icon, label, `{N} × {rate} = {amount} XP`) and the total. The animation below is a motion layer over that settled screen: it plays once when the score arrives, and a restore, a rotation during or after the animation, reduced motion or an unavailable score show the settled screen directly. A rotation while the score is still loading does not stop the animation, since nothing has played yet.

**The animation renders `xpBreakdown`'s stored values, never a recomputation.** For a freshly-finished session this is the config in force right now; for a past session reopened later it is whatever was actually awarded, even if `XpConfig` has since changed. The `{N} × {rate}` notation below is illustrative — it shows the count and the rate that produced the figure — but the figure itself always comes from the stored field, so a later config change can never make an old Summary's total drift from what was actually committed.

### Animation sequence

1. **Total XP counter** sits under the app bar, reading "0 XP", with a Skip button in the app bar. The title and close button are not shown yet.
2. Item tiles are revealed one at a time beneath it, each showing the stored `xpBreakdown` field as an equation built from its count and rate (a caption for lines that are not multiplications), and each adding its amount to the counter, which counts up for a gain and down for a loss. The list scrolls to keep the newest tile in view.
3. After the last tile and a short pause, confetti bursts from the total when it is positive, and the tile list fades out.
4. The **Level card** drops in as the list fades, showing where the User stood before the session. The card and the headline push the total down, the stat pills fade in, and the headline, total and pills glide to the middle of the space between the card and the sheet. The settled stack, top to bottom, is the Level card, then the headline, the total and the stat pills midway down; it scrolls when it does not fit.
5. The card switches to the User's position after the session, and the earned XP **pours** into the Level bar from the starting position. Every Level crossed fills the bar, resets it to empty and pulses the Level number. Levels crossed in the middle show only the bar and the number, because their thresholds are not known; the "x / y XP" text counts only for the first and last Level. A negative total drains the bar within the same Level. The first and last Level each take under a second, and every Level crossed in the middle a quarter of one.
6. The bottom sheet rises and the app bar's title and close button fade in, overlapping the end of the pour. Skip disappears and the breakdown button appears.

Skip or the first system back jumps to the end: everything still moving finishes with a short transition, and the Level card shows the after position without a pour; confetti already under way finishes, and a skip before the burst means no burst. TalkBack announces the total and any Level-up once the screen has settled. The total stays on the screen's centreline throughout; the breakdown button sits beside it without moving it.

**The pour is the Level card's own animation**, not the Summary's: the Summary only shows the card's before position and then its after one, and its timeline never waits for the pour to end. The same card pours on Home whenever the live Level moves while Home is open. It pours only a rise or a change within one Level; a lower Level (another User) and a system with animations off snap to the new figures.

### Rules

- Items worth 0 XP are omitted entirely
- A loss uses the error colour for its tile
- `Session Completed` is omitted for abandoned sessions
- Lines appear in a fixed order: New Cards, Card Mastery, Partial, Mastery Defense, De-mastery, Time Studied, Streak, Session Completed, Daily Goal
- A Fast session has no Rated-only lines

## Firestore storage

Stored on a client-owned per-user singleton, `users/{uid}/progress/user-stats`:

- `xp`: total XP currently held
- `level`: current level (denormalized for fast reads)
- `xpIntoCurrentLevel`: XP accumulated within the current level
- `currentStreak` / `bestStreak`: consecutive study days, and the historical peak
- `lastStudyDate`: `yyyy-MM-dd` local calendar date of the most recent session counted toward the streak
- `goalMetDate`: `yyyy-MM-dd` local calendar date on which the daily goal was most recently met

**Not on `users/{uid}` itself.** Entitlement is not a field on that document — it is the separate subcollection `users/{uid}/entitlement/premium` (`functions/src/lib/entitlement.ts`), written only by the Admin SDK and read server-side by the premium Cloud Function; that subcollection stays default-denied regardless of any rule on the parent document. Scoring state lives under `state/` instead simply to keep `users/{uid}` reserved for identity and admin-managed data. See [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md).

The daily goal itself is **not** stored here — it is device-scoped local state, already written by Settings.

`lastStudyDate` and `goalMetDate` are load-bearing, not conveniences: streak continuation needs to know whether a session today has already been counted, and the daily-goal award needs to know whether today's goal was already met. Neither is derivable from the other stored fields without replaying session history. Both are stored as strings because they are calendar days, not instants.

De-mastery reduces both `xp` and `xpIntoCurrentLevel`. **Level never decreases** — if a loss would push `xpIntoCurrentLevel` below 0, it clamps to 0 at the current level's floor, and `xp` clamps by the same amount so the two stay consistent.

Day attribution uses a session's **start** timestamp in the device's local timezone, so a session crossing midnight counts toward the day it began.
