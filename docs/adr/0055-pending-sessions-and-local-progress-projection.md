# Pending Sessions are projected onto cached server state, and the Summary waits for the server first

> Status: accepted

## Decision

A finished Study Session is queued on the device as a **Pending Session** until `submitStudySession`
confirms it ([ADR-0049](0049-server-authoritative-session-commit.md)). Three rules make that queue
invisible to the User.

### Pending Sessions count at once, through a data-layer projection

`PendingSessionProjector` replays the signed-in User's Pending Sessions, oldest session start first,
one whole session at a time, on top of the cached server state. `DefaultCardProgressRepository` and
`DefaultScoringStateRepository` read through it, so Card Progress, the progress summary and the
scoring state include every Pending Session. Domain and presentation code keep reading those
repositories and never see the queue. Projection is a data-layer mechanism, not a domain term.

The replay uses the same rules as the server, as pure `core:domain` functions:
`mergeSessionIntoCardProgress`, `calculateSessionXp`, `calculateStreakAndGoalAwards`, and
`scoreSession`, the single client scoring function that runs all three. Each rule runs the shared cases
in `testdata/xp-scoring/` and `testdata/card-progress-merge/` against both the Kotlin and the
TypeScript implementations, so a divergence fails a test in one of the two suites. The replay scores
with the cached XP configuration, as the server scores with its current one at delivery
([ADR-0047](0047-xp-values-behind-a-config-repository.md)).

The Daily Goal needs the seconds already studied that day. `progress/user-stats` stores them as
`studiedSecondsOnLastStudyDate`, and the replay advances the field as it advances `lastStudyDate`, so
the client needs no query of the day's sessions.

### The queue delivers each session to its owner, retries forever and never blocks on a bad entry

- Each entry carries the uid of the User who finished the session. The drain delivers only the
  signed-in User's entries, and the function rejects a uid mismatch. Sign-out leaves the queue alone;
  a User's entries are delivered at their next sign-in. Every sign-in, the one restored at app start
  included, schedules a drain with `KEEP`.
- Transient failures (network, outage, expired token, any unknown error) stop the run and retry, with
  no attempt limit. Permanent rejections (`INVALID_ARGUMENT`, `FAILED_PRECONDITION`,
  `PERMISSION_DENIED`) and entries that no longer parse move to a dead-letter file and the run
  continues. The dead-letter file is write-only diagnostics: nothing replays or projects it.
- After each delivery, the worker re-reads the scoring state and the touched Card Progress documents
  from the server, ignoring failures, and only then removes the entry. The cache then already holds
  the delivered session when the projection stops replaying it.

### The Session Summary shows the server's score, and falls back to a local preview

Finishing a session queues it and schedules a drain with `REPLACE`, so a fresh run reads the new
entry even when another drain is running or backing off. The worker publishes each session's server
score, or a rejection marker, as WorkManager progress and output. `SubmitStudySessionUseCase` watches
that report and returns one of two results:

- **`ServerScored`**, the normal case: the Summary renders every line from the server's score.
- **`LocalPreview`**: a `SessionScore` computed by `scoreSession` over the projected reads. The use case
  reads that baseline *before* it queues the session, so the preview never counts the session twice.
  The fallback comes at the earliest of:
  1. no internet when the Summary opens, or the session could not be queued;
  2. `SERVER_RESULT_BUDGET` (6 seconds) passing;
  3. the server rejecting the session;
  4. the drain run ending, or going back into retry, without this session's score.

The use case returns once. A server score arriving after the fallback does not replace it; other
screens show the server's numbers the next time they read.

## Considered Options

- **Firestore's offline write queue plus a trigger function.** Rejected: the client would write the
  session document itself, which ADR-0049's rules forbid, and the Summary could not learn the score.
- **A revision field that gates the handover** from projected to server state. Rejected: the refresh
  before removal closes most of the gap, and the rest is brief and self-correcting.
- **Versioned XP configurations**, so a Pending Session is scored with the configuration of its
  start. Rejected: the server scores with its current configuration, so a versioned preview would
  disagree with the server.
- **A configuration snapshot per session.** Built, then removed for the same reason. The client scores
  with its cached configuration everywhere.
- **Delivering in-process from the Summary** instead of through the worker. Rejected: two delivery
  paths for one queue, and the Summary dies with its process.
- **An app-lifetime snapshot listener on the scoring state**, to keep it cached. Deferred until a
  screen needs a live scoring state.
- **A client query of the day's sessions** for the Daily Goal. Rejected: nothing keeps session
  documents cached, so the query is empty offline, exactly when it is needed.

## Consequences

Accepted transient inaccuracies, all corrected by the next successful server read:

- **Under-count after delivery.** Between removal and a cache update (the refresh failed), a
  delivered session is in neither the queue nor the cache. A fallback preview in that window also
  misses it.
- **Double count after a crash.** A process death between delivery and removal leaves the entry
  queued and replayed over a cache that includes it, until the next drain resubmits it. The function
  is idempotent per session id, so the resubmission changes nothing on the server.
- **Double count between refresh and removal.** For that brief moment the cache includes the session
  and the queue still holds it.
- **The fallback race.** The preview's baseline is read before submission, so a drain that delivers an
  older Pending Session during the wait is not reflected in it.
- **Daily Goal under-count.** A Pending Session dated before `lastStudyDate` counts only its own
  seconds, and sessions delivered from another device count only once this device reads the scoring
  state again.
- **Seconds field not yet written.** Until the function has rewritten a User's `user-stats` document
  after the deploy that added `studiedSecondsOnLastStudyDate`, the field is absent and reads as 0, so a
  replay on the day of `lastStudyDate` starts the Daily Goal's minutes from zero. The next delivered
  session writes it.
- **Multi-device Streak.** Each device projects from its own cached scoring state, so two devices
  studying offline on the same day each preview a Streak award that the server pays once.
- **Empty Card Progress baseline.** Offline, a Subcategory never read on this device has no cached
  document, so a Pending Session's cards replay over no record: they count as new, and a Mastery
  Defense card counts as newly Mastered.
