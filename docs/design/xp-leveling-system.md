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

- Confetti/celebration animation on the Progress screen, and (designed, not built yet) on the Session Summary screen when the level-up happened during that session
- Milestone badges unlocked at levels 5, 10, 25, 50, 100 — displayed on the Progress screen
- Badge/achievement system detail: deferred to a separate design session
- No XP burst on level-up (keeps the curve clean)

## Session Summary XP presentation

XP is calculated **on the Session Summary screen**, from the session result and the `XpConfig` snapshot the session carried, and written to Firestore there as the `xpBreakdown` map on the session document ([Session Stats & Data Model](session-stats-data-model.md)), as part of the single session-commit batch ([ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md)). The screen shows the total with an info button that opens the itemized breakdown in a dialog: one row per source (icon, label, `{N} × {rate} = {amount} XP`) and the total. The sequential "pour" animation below is designed but not built yet: it will be a motion layer over that settled screen.

**The animation renders `xpBreakdown`'s stored values, never a recomputation.** For a freshly-finished session this is the config in force right now; for a past session reopened later it is whatever was actually awarded, even if `XpConfig` has since changed. The `{N} × {rate}` notation below is illustrative — it shows the count and the rate that produced the figure — but the figure itself always comes from the stored field, so a later config change can never make an old Summary's total drift from what was actually committed.

### Animation sequence (designed, not built)

1. **Total XP counter** appears prominently at top, starting at 0, counting up to the stored `xpTotal`.
2. First **item tile** slides up from below into view, showing a math equation built from the stored `xpBreakdown` fields:
   - `{N} new cards × 10 = {xpBreakdown.newCards}` (New Cards)
   - `{N} cards × 100 = {xpBreakdown.mastered}` (Card Mastery)
   - `{N} cards × 25 = {xpBreakdown.partial}` (Partial)
   - `{N} cards × 50 = {xpBreakdown.masteryDefenseBonus}` (Mastery Defense)
   - `{N} cards × 80 = -{xpBreakdown.demastered}` (De-mastery)
   - `{N} min × 10 = {xpBreakdown.timeStudied}` (Time Studied)
   - `{N} day streak × 250 = {xpBreakdown.streakBonus}` (Streak, capped display)
   - `Session completed = +{xpBreakdown.sessionCompletionBonus}` (flat, no multiplier)
   - `Daily goal met = +{xpBreakdown.dailyGoalBonus}` (flat, no multiplier)
3. The XP value after the `=` sign counts **down to 0** while the total counter counts **up** by the same amount simultaneously ("pouring" the number into the total).
4. Item disappears once its value reaches 0. Next item slides up.
5. After all items are consumed: session total animates into the user's overall XP progress.
6. If a level-up occurred: level-up celebration (confetti + new level). Multiple level-ups play sequentially.

### Rules

- Items worth 0 XP are omitted entirely
- De-mastery items use error color (red) for the equation and the pour
- `Session Completed` omitted for abandoned sessions
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
