# Session commit and XP scoring move server-side, into a single Cloud Function

> Status: accepted

## Decision

A single `onCall` Cloud Function, `submitStudySession`, becomes the sole writer of a finished session's
Firestore state: `sessions/{sessionId}`, the touched per-Subcategory progress documents,
`progress/summary` and `progress/user-stats` — all four inside one Firestore transaction, keyed for
idempotency on the client-generated `sessionId`. The client's job shrinks to submitting what happened —
per-card Ratings, duration, whether the session was abandoned — through a durable offline queue, and
showing a non-authoritative optimistic XP preview computed by its own kept, pure calculation. Firestore
rules reject any direct client write to the four documents above; only the function's Admin SDK context
can write them.

## Context

Two findings drove this, against the prior design (ADR-0014): a code-review finding
that the client-issued scoring-state write was a fire-and-forget batch, not a transaction, so two
sessions committing close together — two devices, or a Fast and a Rated session finishing near-
simultaneously — could silently clobber each other's XP and level with no error and no record it
happened; and the more fundamental recognition that a client computing and writing its own XP, level and
mastery is trivially tamperable by a modified client, however carefully the write itself is coded.
Server-side computation inside one transaction closes both at once: the transaction serializes
concurrent writes correctly, and Firestore rules deny any client write to the documents the function
owns, so trust moves from "whatever the client says" to "whatever the function computes."

## Considered Options

- **A Firestore-trigger function**, reacting to a client-written session document — rejected in favor of
  matching the one `onCall` transport pattern this codebase already uses for every other function
  (the voice-grading callables), rather than introducing a second.
- **Fix the race with more careful client code** (e.g. a client-issued Firestore transaction instead of a
  batch) — rejected: it closes the lost-update race but does nothing about tampering, since the client
  would still be the one deciding what XP a session earned.
- **Keep client writes, add server-side plausibility validation only** (reject obviously-wrong values) —
  rejected as insufficient: bounds-checking can catch implausible numbers but can't recompute XP/level
  any more cheaply than making the computation authoritative in the first place.

## Consequences

- The XP/level calculation now exists in two independent implementations: the kept Kotlin
  `CalculateSessionXpUseCase`, repurposed as an optimistic-preview-only calculator, and a TypeScript port
  inside the function that is the one with actual write authority. They are expected to agree in the
  overwhelming common case and are each tested against mirrored fixtures, but nothing in this codebase
  shares the logic across languages — keeping them in sync is a manual discipline, not a compiler
  guarantee.
- Submitting a session becomes durable-but-asynchronous rather than synchronous: a local queue plus a
  WorkManager drain job replaces the old direct Firestore write. There is no reconciliation UI — if the
  optimistic preview and the authoritative result differ (the rare concurrent-commit case this ADR
  exists to close), the Summary screen does not retroactively correct itself; every other screen that
  reads scoring state simply reads the same documents the function writes, showing the right number next
  time it loads.
- A session that sits in the offline queue through an `XpConfig` redeploy scores against whatever config
  is live when it finally syncs, not what was in force when it was actually played — an accepted,
  low-stakes drift (`XpConfig` is local/hardcoded, changing only on infrequent releases), not versioned.
- `sessions/{sessionId}`, `progress/{docId}` (covering both `progress/summary` and
  `progress/user-stats`) and `progress/details/subcategories/{id}` are client-read-only in
  `firestore.rules`; no other rule block is affected.
- The function still trusts the client's submitted per-card Ratings and terminal states — there is no
  server-side ground truth for what actually happened during a study session. This closes formula
  tampering and race-condition exploits; it does not close a client lying about which cards it answered
  correctly. Basic plausibility bounds-checking on submitted ratings is a natural, low-cost follow-up,
  not built here.
- Streak and daily-goal awarding was designed against this architecture rather than
  its own original client-side plan, once this landed — see
  [ADR-0048](0048-streak-and-daily-goal-ride-the-session-payload.md).

## Amendments

**2026-09-30 — the Summary waits for the server, and the local preview is a fallback.** Three
consequences above changed. See [ADR-0055](0055-pending-sessions-and-local-progress-projection.md).

- The Session Summary now waits up to 6 seconds for the server's score and renders it. The local
  preview, computed by `scoreSession`, shows only when the device is offline, the server rejects the
  session, the drain ends without its score, or the wait runs out. `CalculateSessionXpUseCase` is
  gone. The client's scoring rules now run shared test cases against the TypeScript ones, so a
  divergence fails a test instead of relying on manual discipline.
- The delivery queue has no attempt limit. Transient failures retry forever; permanent rejections move
  the entry to a dead-letter file.
- The configuration drift note is replaced. `XpConfig` is a server-owned Firestore document, and the
  server always scores with its current copy (ADR-0047's amendment).
