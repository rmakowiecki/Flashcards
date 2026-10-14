# Session Submission

What happens to a Study Session once it ends: how it waits on the device as a **Pending Session**, how it is delivered to the server, and what the server records for it, including the Streak and Daily Goal rules and how the User's scoring state moves. The persisted shapes are in [firestore-schema.md](firestore-schema.md).

## What a session records

A Study Session of either Study Mode is recorded once, after it terminates. Nothing is written while it runs. The session seals its **Session Result** at termination; which Flashcards that result includes, on a natural end and on an early exit, is described in [study-session.md](study-session.md#session-termination). A session with no Studied Flashcards is never submitted, and an app kill during a session records nothing.

The `submitStudySession` Cloud Function records the result in one Firestore transaction ([ADR-0049](../adr/0049-server-authoritative-session-commit.md)). In that transaction it:

- writes the session document, with its Flashcard Results embedded;
- merges Card Progress into one document per Subcategory the session touched;
- increments the progress summary's Studied and Mastered counts;
- overwrites the User's scoring state (XP, Level, Streak, Daily Goal state);
- adds the session to the User's Recents ([ADR-0057](../adr/0057-recents-state-projection.md)).

The client writes none of these documents. The server decides from its own stored Card Progress which Flashcards are new to the User and which were already Mastered; it does not trust the client's copy of either. Because the transaction re-reads everything it changes, two sessions submitted at once from two devices are applied one after the other, never on top of the same stale state.

**Submission is idempotent per session id.** If a session document already exists, the function answers from it alone and writes nothing. The document stores the XP breakdown, the rates it was scored with and the User's Level position before and after, so the answer never changes, however the XP configuration or the User's state has moved since.

## Abandoned sessions

A session the User exits before the deck end is recorded exactly like a finished one and marked abandoned. It counts toward the Streak, toward the day's minutes for the Daily Goal, toward time-studied XP, and toward Card Progress for every Flashcard that reached the User. It earns no session-completion XP and never counts toward **Sessions Completed**.

## Duration

A session's duration runs from the moment its first Flashcard is on screen to its termination. The clock never pauses: backgrounding the app or pausing playback does not stop it. Time on the Session Summary screen is excluded, and a session whose Flashcards never loaded has a duration of zero. Time-studied XP counts whole minutes only.

## Study day

Every session belongs to one local calendar day, its **study day**: the day of its start instant in the device's timezone, so a session crossing midnight counts toward the day it began. The device records its UTC offset when the session starts, so a timezone change during the session does not move its day. The server derives the study day from the start instant and that offset, and never accepts a date from the client ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)).

## Streak

The **Streak** is evaluated against the last study day the scoring state recorded:

- A session on the day right after it extends the Streak by one.
- A session on any later day restarts the Streak at one, and so does the User's first session ever.
- A session on that same day, or on an earlier day, leaves the Streak and the last study day unchanged and earns no Streak XP.

The stored Streak is not reset when a day passes without a session; the next counted session restarts it. Best Streak is the highest Streak ever reached and never decreases. A session that extends or restarts the Streak earns Streak XP ([xp-leveling-system.md](xp-leveling-system.md)).

## Daily Goal

The **Daily Goal** never reaches Firestore. Each submission carries the goal in effect when its session ended, and the server judges that session against it, so a goal edited mid-day applies to the next session submitted ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)).

The day's minutes are the durations of every recorded session on the submission's study day plus the submitted session's own, counted in whole minutes. The Daily Goal XP is awarded when those minutes reach the goal, the goal has not already been met on that day, and the day is not earlier than the last day the goal was met. It is therefore awarded at most once per day.

The scoring state also keeps the total seconds studied on its last study day. A submission on or after that day sets it to the day's total; an earlier one leaves it unchanged. The device starts from it when it previews the Daily Goal for a Pending Session.

## Scoring state

The session's XP total is applied to the User's Level position. A gain climbs through every Level threshold it crosses. A loss is clamped at the floor of the current Level, so Level never decreases ([CONTEXT.md](../../CONTEXT.md), **XP**). The session's id is appended to the list of sessions applied to the scoring state, which the device uses to stop counting a delivered session twice ([ADR-0061](../adr/0061-delivered-sessions-applied-once-in-the-projection.md)).

A session delivered late, after a session from a later day was already recorded, still earns its card, time and completion XP. Only its Streak and Daily Goal effects are lost, because neither date moves backward.

## Delivery

A session that ends is queued in a file on the device, stamped with the uid of the User signed in at that moment, and becomes a **Pending Session**. It counts on that device at once: every read of Card Progress, the progress summary and the scoring state replays the User's Pending Sessions over the cached documents with the same rules the server uses, which shared test cases keep in step ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)).

- **When delivery runs.** A delivery run starts as soon as a session is queued, replacing any run that is waiting, so the Session Summary is not left waiting behind a backoff. One also starts on every sign-in, the sign-in restored at app start included, which picks up sessions left by an earlier process or queued before a sign-out. A run starts only while a network is available.
- **Whose sessions.** A run delivers only the signed-in User's sessions, oldest start first; other Users' sessions wait in the queue until their User signs in. If the signed-in User changes during a run, the run stops. The server also rejects a submission whose owner is not the caller.
- **Network or server trouble.** The run stops, and the failed session and every later one stay queued. The run is retried later with backoff and no attempt limit, so a poor connection never makes the device give up on a session.
- **Rejection.** A session the server rejects, or a queue entry that can no longer be read, is moved to a separate local file kept for diagnostics. It stops being a Pending Session and is no longer counted.
- **After delivery.** The device re-reads the scoring state and the touched Card Progress from the server, then removes the session from the queue. If a read fails, the session stays queued and is submitted again later, which the server answers from the stored session. The run reports each session's score as it goes, which is what a waiting [Session Summary](session-summary.md) shows.
- **Account Deletion** drops the User's Pending Sessions ([ADR-0060](../adr/0060-account-deletion-completes-on-the-device.md)).

## Related

- [firestore-schema.md](firestore-schema.md): the session, Card Progress, progress summary, scoring state and Recents documents.
- [study-session.md](study-session.md): termination and which Flashcards a Session Result includes.
- [session-summary.md](session-summary.md): how the Summary waits for the server's score or previews it.
- [xp-leveling-system.md](xp-leveling-system.md): XP sources and the Level curve.
- [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md): one record per session, written once it ends.
- [ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md), [ADR-0049](../adr/0049-server-authoritative-session-commit.md), [ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md).
