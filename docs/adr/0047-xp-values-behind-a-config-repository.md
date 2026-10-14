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

The configuration is server-owned. Its source of truth is the Firestore document `config/xp`, written
only by the Admin SDK. The `submitStudySession` function scores every session with its current copy
of that document, and falls back to bundled defaults if the document is missing or invalid.

The client refreshes its copy best-effort on every sign-in and keeps it on the device, so it can
preview XP offline with the last rates it saw. It uses `XpConfig`'s bundled defaults only until a
first fetch succeeds.

### A session is scored with the configuration current at delivery

No configuration snapshot is taken when a session starts. The server scores a session with the
configuration current when the session is delivered, and stores the rates it used on the session
document, so a session once scored never changes. The Session Summary's fallback preview and the
projection of Pending Sessions both score with the device's cached copy. No configuration is
versioned. See [ADR-0055](0055-pending-sessions-and-local-progress-projection.md).

## Context

The XP design fixes a full set of numbers — mastery, defense, de-mastery, completion, daily goal,
streak, time — and describes them as tunable constants chosen at implementation time. The level
curve is worse than tunable: its shape is specified but its `base` and `exponent` were never chosen
at all, so a level is not computable from the design as written.

Both facts point the same way. These are balance values, not domain rules. A domain that hardcodes
them can only be rebalanced by shipping a release, and the numbers most likely to need rebalancing
are exactly the ones a live user population reveals as wrong.

Scoring is server-authoritative ([ADR-0049](0049-server-authoritative-session-commit.md)), so the
configuration that counts is the one the server holds. A per-session snapshot taken on the device
would never reach the server, and a preview scored from it could disagree with the server's score for
the same session.

## Alternatives considered

**Constants in the domain layer** — rejected. Rebalancing requires a release, and the level curve
would have to be pinned now on no evidence.

**Firebase Remote Config directly at each call site** — rejected. It puts an Android dependency in
`core:domain`, which the layering rules forbid, and scatters defaulting logic across every consumer.

**Capture the configuration when a session starts and score the session with it** — rejected. It
guarantees a session is scored with the numbers the User was playing for, but the server never sees
the snapshot, so the device's preview and the server's score could disagree. Accepting that a config
change can affect a session still queued is the simpler trade-off.

**A local, hardcoded configuration with a remote source added later** — rejected. Scoring moved to
the server, which needs a configuration of its own; a device-only source would leave the server
without one.

## Consequences

- The level curve is not pinned in code. Choosing `base` and `exponent` becomes a config change.
- XP must be computable offline, so the client keeps a cached copy with the bundled defaults as its
  starting point.
- A session queued on the device across a configuration change is scored with the configuration
  current at delivery, not the one in force when it was played. Its preview on the device and the
  server's score can differ until the device's copy is refreshed.
- Two sessions delivered either side of a configuration change compute different XP for identical
  performance. This is intended. Each session document stores the rates it was scored with, so a
  past Summary never changes.
