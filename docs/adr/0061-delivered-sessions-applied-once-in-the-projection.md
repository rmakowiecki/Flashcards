# Delivered sessions are applied once in the projection

> Status: accepted

Supersedes the rejection of "a revision field that gates the handover" in
[ADR-0055](0055-pending-sessions-and-local-progress-projection.md).

## Context

`PendingSessionProjector` replays every Pending Session over the cached server state
([ADR-0055](0055-pending-sessions-and-local-progress-projection.md)). Nothing told it whether that state
already included a queued session, so a session could be counted in both places, or in neither:

- **Double count between refresh and removal.** The worker refreshed the cache, then removed the entry.
- **Double count after a lost response or a crash.** The server committed, but the entry stayed queued
  until a later drain, possibly for a long WorkManager backoff.
- **Under-count after delivery.** A failed refresh was ignored and the entry removed, so the session was
  in neither the queue nor the cache.
- **A doubled award on a restored Summary.** After process death the Summary submitted again while the
  session was already queued, so the preview's baseline already included the session.

XP and Level are additive, so a double count shows a wrong number and can flash a false Level-up. Home,
Recents, Progress and the Session Summary must show the same numbers.

## Decision

### `user-stats` lists the sessions applied to it

`submitStudySession` keeps `appliedSessionIds` on `progress/user-stats`: the ids of the latest 20
sessions applied to it, oldest first. The commit transaction appends its own id in the same write as
the scoring fields. A retry answers from the session document and never touches the list. The list
stays out of the shared `ScoringState` type and the XP scoring code: the device reads it only to tell
which Pending Sessions the cached document already includes.

### An applied Pending Session adds to no aggregate

A Pending Session listed in the cached `appliedSessionIds` is replayed for its Card Progress only, since
that merge is idempotent and covers a Card Progress cache older than `user-stats`. It does not advance
the projected scoring state, the Streak or the Daily Goal seconds, and adds no Studied or Mastered
delta. Recents still get its `xpTotal`, scored against the projected state without advancing it, until
the server's Recents entry replaces the row.

If the scoring state is unreadable when the progress summary's deltas are computed, no session counts
as applied: a double count is better than dropping real deltas on a guess. The deltas are recomputed
when the queue changes and whenever the live progress summary changes. The server writes the summary
and `user-stats` in one transaction, and the recompute reads `user-stats` from the server when online,
so the summary never includes a session the recomputed deltas still add. This also covers a lost
response: the summary listener sees the commit even though the worker does not.

### The worker removes an entry only after a complete refresh

After a successful submission, the worker publishes the delivery report, then reads `user-stats` and
the Card Progress of every Subcategory the session's Flashcard Results touch from the server. It
removes the entry only if every read succeeded. Otherwise the entry stays queued and the run retries;
the next run re-submits it, the server answers from its stored session, and that run refreshes and
removes it. A failed refresh never dead-letters an entry.

So a session is counted through the queue until the cache includes it, and through the cache once it
leaves the queue.

### The Summary never builds on its own session

`ScoringStateRepository.getScoringState` and `CardProgressRepository.getProgress` take an
`excludedSessionId`. The projection leaves that session out of the queue before replaying. If the cached
`user-stats` already lists it as applied, the scoring-state read fails: the cache includes the session
and a baseline cannot subtract it, so the Summary shows no preview. `SubmitStudySessionUseCase` passes
its session's id to both baseline reads.

The Summary saves a resolved score, server or preview, in its `SavedStateHandle`. A ViewModel restored
after process death shows the saved score and does not submit again. Only a death before the score
resolved submits again, and the exclusion covers that. A failed preview is not saved, so a restore
tries again.

## Considered Options

- **The list on `recents/state`.** Rejected: a separate document from the scoring state the projection
  reads, and capped at the newest 15 sessions by start time, so a late-delivered old session would not
  appear in it.
- **A watermark of the newest applied session's start time.** Rejected: sessions delivered from other
  devices advance it, so a device could skip its own older Pending Session that the server has not
  applied yet.
- **Reading the session document** to check whether the server holds a session. Rejected: session
  documents are not cached, so the check fails offline, exactly when Pending Sessions matter.
- **Accepting the windows**, as ADR-0055 did. Rejected once the Summary and Home show the Level from
  the same projection while the worker delivers.

## Consequences

Residual windows:

- **A partial refresh.** If Card Progress was refreshed but `user-stats` was not, a fallback preview in
  that moment counts this session's cards as already studied. The window closes when the next drain
  retries.
- **Twenty other sessions.** If twenty other sessions (from other devices) are applied between a
  delivery and its refresh, the id leaves the list and the session counts twice until it leaves the
  queue. Practically impossible.
- **A failed `user-stats` read on recompute.** If the server read fails and the cached `user-stats`
  predates the commit, the recomputed deltas still add the session until the next summary or queue
  change.
- **One stale summary emission.** A live summary change emits once with the previous deltas before
  they are recomputed, for the length of one cache read.
- **A restored Summary with no preview.** If the session was delivered and the cache refreshed before a
  process death that came before the score resolved, the restored Summary shows no score.
