# XP values live behind a config repository, not in domain constants

## Decision

Every XP award, every penalty and the level-curve parameters live in an `XpConfig` value object
served by an `XpConfigRepository` in `core:domain`. No XP number is a constant in domain logic.

```
newCardStudied: Int
cardMastered: Int
cardPartial: Int
masteryDefended: Int
cardDemastered: Int          // negative
sessionCompleted: Int
dailyGoalMet: Int
streakPerDay: Int
streakMaxPerDay: Int
minuteStudied: Int
levelCurveBase: Double
levelCurveExponent: Double
```

The implementation today is local and hardcoded. The repository seam exists so the values can move
to a remote source later without touching a single call site.

### A session uses the config as of its start

The `XpConfig` is fetched alongside the session's Flashcards, captured into the session state when
the clock starts, and carried in the session result. The Summary screen computes its breakdown from
that captured snapshot, not from a fresh read.

## Context

The XP design fixes a full set of numbers — mastery, defense, de-mastery, completion, daily goal,
streak, time — and describes them as tunable constants chosen at implementation time. The level
curve is worse than tunable: its shape is specified but its `base` and `exponent` were never chosen
at all, so a level is not computable from the design as written.

Both facts point the same way. These are balance values, not domain rules. A domain that hardcodes
them can only be rebalanced by shipping a release, and the numbers most likely to need rebalancing
are exactly the ones a live user population reveals as wrong.

The snapshot rule is a smaller point with a sharp edge. If the values can change remotely, they can
change while a session is in progress. Computing a session's XP from values read at the end means a
config push mid-session silently rewrites the arithmetic for a session already underway, and the
number the Summary shows is not the number the user was playing for.

## Alternatives considered

**Constants in the domain layer** — rejected. Rebalancing requires a release, and the level curve
would have to be pinned now on no evidence.

**Firebase Remote Config directly at each call site** — rejected. It puts an Android dependency in
`core:domain`, which the layering rules forbid, and scatters defaulting logic across every consumer.

**Read the config fresh when the Summary computes XP** — rejected. It is simpler and it is the bug:
a mid-session change alters a session retroactively.

**Ship the remote source now** — rejected as premature. The seam is what has to exist early; the
transport can arrive whenever rebalancing actually becomes necessary.

## Consequences

- The session state carries an immutable `XpConfig` field, and the session result carries it
  forward, so the Summary needs no repository read to compute its breakdown.
- The level curve is not pinned in code. Choosing `base` and `exponent` becomes a config change.
- A future remote source needs a cache and a defaulting story, since XP must be computable offline.
  The local implementation is that default.
- Two sessions run either side of a config change compute different XP for identical performance.
  This is intended, and the session record's stored counts allow a historical session's breakdown to
  be recomputed under whichever config is current if that is ever wanted.

## Amendments

**2026-09-30 — the configuration is server-owned, and the session-start snapshot is removed.** The
source of truth is the Firestore document `config/xp`, written only by the Admin SDK. The
`submitStudySession` function scores every session with its current copy of that document. The
client refreshes its copy best-effort on every sign-in and keeps it on the device, so it works
offline with the last rates it saw. It falls back to `XpConfig`'s bundled defaults only until a first
fetch succeeds. The "local and hardcoded" implementation and the "remote source later" alternative
above are superseded.

The per-session snapshot rule is removed. The server never saw the snapshot, so a preview scored from
it could disagree with the server's score for the same session. The Session Summary's fallback
preview and the projection of Pending Sessions both score with the cached configuration. A session
queued across a configuration change is scored with the configuration current at delivery, and no
configuration is versioned. See [ADR-0055](0055-pending-sessions-and-local-progress-projection.md).
