# Streak and daily-goal minutes ride the session payload, not stored server state

> Status: accepted

## Decision

The Daily Goal (minutes/day) is never written to Firestore. The client reads it from local DataStore
when a session ends and sends it as `dailyGoalMinutes` on the `submitStudySession` payload; the function
evaluates the day's cumulative minutes against whatever value arrives with each submission. There is no
day-locked snapshot and no server-side memory of "the goal that governed today" — editing the goal
mid-day takes effect on the very next session *submitted* that day, not the next calendar day.

The session's local calendar day (`studyDate`, `yyyy-MM-dd`) is likewise computed client-side, from the
session's start instant and the device's own timezone, and rides the payload the same way — the server
has no way to derive a device-local calendar day from a UTC instant on its own.

Both `lastStudyDate` (streak) and `goalMetDate` (daily-goal gate) on `progress/user-stats` only ever move
forward: a submission whose `studyDate` is not later than the stored value never advances the streak,
never fires the goal bonus, and never overwrites either date backward. An out-of-order arrival — a
queued offline session from an earlier day, delivered after a later day's session already committed — is
accepted as a silent no-op for these two awards specifically, not reconciled.

## Context

The original design planned streak and daily-goal entirely client-side, and rejected storing
the Daily Goal in Firestore ("a second writable source with no sync story"). Moving all
scoring computation server-side then reopened the question: a server-side calculation needs to see the goal
to gate the bonus on it, but the objection to a synced Firestore copy — routing every Settings-screen
edit through a second write path — holds regardless of where the calculation runs.

The alternative considered at length was a day-locked design: the function stores the goal value in
effect at the day's first session and holds it fixed against later same-day edits, mirroring the
[XpConfig snapshot rule](0047-xp-values-behind-a-config-repository.md). This closes a real gap — under
live evaluation, a user who studies under a higher goal, falls short, then lowers the goal and submits a
trivial top-up session gets the flat daily-goal bonus for minutes studied under a threshold they hadn't
actually met at the time. Day-locking was rejected anyway: it still needs *some* server-side memory of
"today's locked value" even without a synced preference doc — derived once from whichever payload is
first each day, held on the already-function-owned `progress/user-stats` document — and the accepted
trade-off was to keep the mechanism as simple as the streak fields already sitting on that document, and
treat the gap as low-stakes: the bonus is flat and capped once per day, so gaming it nets at most one
day's `dailyGoalMet` award.

## Considered Options

- **Firestore-stored Daily Goal, client-writable field** — rejected. Reopens the exact "second writable
  source" objection raised originally, now with a live-read requirement on top.
- **Firestore-stored Daily Goal, dedicated doc, day-locked read** — rejected for the same write-plumbing
  cost; still needs the function to remember which value was locked, so it relocates state-tracking
  rather than removing it.
- **Day-locked via a derived field on `progress/user-stats`, no synced preference doc** — the design that
  actually delivers day-locking without a client sync path. Rejected once the farming gap it closes was
  judged low-stakes against the added state it introduces.

## Consequences

- A user can influence today's own goal-bonus outcome by editing their goal after already studying —
  accepted, not solved.
- The daily-goal bonus is not reproducible from `progress/user-stats` alone after the fact; it depends
  on whatever `dailyGoalMinutes` rode the triggering submission, not a value persisted independently of
  the session document's own stored breakdown.
- `studyDate` becomes a new field on both the `submitStudySession` payload and the persisted
  `sessions/{sessionId}` document — needed for streak/goal day comparison and for querying "today's
  sessions" without server-side timezone math.
- Out-of-order submission delivery (multi-device, or a long-queued offline backlog crossing a streak
  boundary) can silently under-award streak/goal — the same class of accepted drift as `XpConfig`'s own
  redeploy-mid-queue gap (ADR-0047).

## Amendments

**2026-09-30 — the client previews the Streak and Daily Goal, and `user-stats` stores the day's
seconds.** The client ports the Streak and Daily Goal calculation (`calculateStreakAndGoalAwards`),
checked against the server's by shared test cases, for the Session Summary's fallback preview and the
projection of Pending Sessions. The server stays the authority: only its awards are written.

The Daily Goal needs the day's studied time, and the client has no cached copy of the day's sessions.
The function therefore stores `studiedSecondsOnLastStudyDate` on `progress/user-stats`: the seconds of
every recorded session on `lastStudyDate`. A submission sets it to the day's total when its
`studyDate` is not earlier than `lastStudyDate`, and leaves it unchanged otherwise. The client reads
it as the day's baseline and advances it while replaying Pending Sessions. See
[ADR-0055](0055-pending-sessions-and-local-progress-projection.md).
