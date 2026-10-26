# Pending Sessions are projected onto cached server state, and the Summary always shows the local score

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
  from the server, and only then removes the entry. (Amended by
  [ADR-0061](0061-delivered-sessions-applied-once-in-the-projection.md): a failed read keeps the entry
  queued.) The cache then already holds
  the delivered session when the projection stops replaying it.

### The Session Summary always shows the local score

Finishing a session queues it and schedules a drain with `REPLACE`, so a fresh run reads the new
entry even when another drain is running or backing off. Nothing waits for that run.
`SubmitStudySessionUseCase` reads the score's baseline over the projected reads, queues the session,
then scores it with `scoreSession` and returns that `SessionScore`. The baseline is read *before* the
session is queued, so the score never counts the session twice. The baseline reads only the Firestore
cache, never the server: the session's own load read its Card Progress moments before, and the live
Level streams keep the scoring state cached. A Card Progress document the cache holds nothing for reads
as no record, so its cards count as new; a scoring state the cache holds nothing for fails the score. Online and offline Summaries are the
same path. `submitStudySession` answers with nothing: what the server records reaches the device only
through the post-delivery refresh, and every other screen shows it the next time it reads.

A session that could not be queued at all (no signed-in User, a failed local write) gets no score:
the use case fails with `SessionNotSavedException` and the Summary says the session was not saved.

## Considered Options

- **Firestore's offline write queue plus a trigger function.** Rejected: the client would write the
  session document itself, which ADR-0049's rules forbid, and the Summary could not learn the score.
- **A revision field that gates the handover** from projected to server state. Rejected: the refresh
  before removal closes most of the gap, and the rest is brief and self-correcting. **Superseded by
  [ADR-0061](0061-delivered-sessions-applied-once-in-the-projection.md)**, which gates it with the
  applied session ids on `user-stats`.
- **Versioned XP configurations**, so a Pending Session is scored with the configuration of its
  start. Rejected: the server scores with its current configuration, so a versioned preview would
  disagree with the server.
- **A configuration snapshot per session.** Built, then removed for the same reason. The client scores
  with its cached configuration everywhere.
- **Waiting for the server's score**, with the local score as a fallback. Built first: the Summary
  waited up to 6 seconds for the drain to report the server's score. Rejected: a cold function, the
  WorkManager start and the transaction made the wait run out on most sessions, so the Summary showed a
  spinner and then the fallback anyway, through a second code path. A shorter wait keeps both paths and
  the spinner.
- **Making the server fast enough to wait for** (a minimum instance, expedited work, a region near the
  Users). Rejected: it costs money, still guarantees nothing, and the offline path must exist anyway.
- **Default-source baseline reads for the score.** Built first. Rejected: offline, Firestore's default
  read of a document the server reported missing fails even though the cache knows it is missing, so a
  first session in a Subcategory, finished offline, got no score. A default read that falls back to the
  cache fixes that but still waits for the server on a flaky connection.
- **Warming the Card Progress cache from Preview.** Rejected: the session's own load already reads it,
  and a session started offline cannot warm anything.
- **Delivering in-process from the Summary** instead of through the worker. Rejected: two delivery
  paths for one queue, and the Summary dies with its process.
- **An app-lifetime snapshot listener on the scoring state**, to keep it cached. Deferred until a
  screen needs a live scoring state; resolved as a listener per collecting screen (see Amendments).
- **A client query of the day's sessions** for the Daily Goal. Rejected: nothing keeps session
  documents cached, so the query is empty offline, exactly when it is needed.

## Consequences

Accepted transient inaccuracies, all corrected by the next successful server read. The first three are
resolved by [ADR-0061](0061-delivered-sessions-applied-once-in-the-projection.md):

- **Under-count after delivery.** Between removal and a cache update (the refresh failed), a
  delivered session is in neither the queue nor the cache. A Summary score in that window also
  misses it.
- **Double count after a crash.** A process death between delivery and removal leaves the entry
  queued and replayed over a cache that includes it, until the next drain resubmits it. The function
  is idempotent per session id, so the resubmission changes nothing on the server.
- **Double count between refresh and removal.** For that brief moment the cache includes the session
  and the queue still holds it.
- **The Summary's score can differ from the server's record.** The server scores with its current
  XP configuration and its own Card Progress, and may already hold a session from another device, or
  reject the session outright. The Summary never corrects itself; Home's Level card and Recents show the
  server's numbers once the session is delivered.
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
  Defense card counts as newly Mastered. The Summary's score does the same for its own session when
  the session started offline.
- **The Summary's score misses changes made elsewhere mid-session.** The cache-only baseline does not
  see progress another device delivered after the session loaded.

## Amendments

**2026-10-03 — Recents consume the projection.** `DefaultRecentSessionsRepository` merges the User's
Pending Sessions into Home's Recents, so a session finished offline shows at once. Through
`PendingSessionProjector` it scores each Pending Session's preview `xpTotal` oldest first with the
cached XP configuration, so later
Streak and Daily Goal awards build on earlier ones. A session with no Studied Flashcards is never
queued, so it never reaches the projection. See [ADR-0057](0057-recents-state-projection.md).

**2026-10-10 — The Level is followed live, one listener per collecting screen.**
`DefaultLevelProgressRepository` serves `LevelProgress` (Level, XP into it, XP that completes it) as a
flow: a snapshot listener on `progress/user-stats` with the User's Pending Sessions replayed on top
through `PendingSessionProjector`. This resolves the deferred app-lifetime listener option without an
always-on listener and without a shared replay: every collecting screen gets its own cold flow and
listener. The Firestore client serves duplicate listeners on one document from a single watch, so a
second screen costs only its own replay. A shared hot flow was rejected: an upstream failure in an
application scope has no collector to reach, its collectors never see completion, and a resubscription
inside a sharing grace window never restarts a completed upstream.

- **Applied sessions add nothing.** The live replay reads `appliedSessionIds` off the same snapshot it
  replays over, so a delivered session still in the queue counts once, as in the one-shot read. See
  [ADR-0061](0061-delivered-sessions-applied-once-in-the-projection.md).
- **Stale replays are dropped.** The live replay reads the Card Progress baselines, then checks the
  queue against the snapshot it replays; a changed queue (a delivery, or a User switch) drops the result
  and the change re-emits. The one-shot read keeps its own uid check and has no stale check, so a
  delivery during the Summary's score read never fails the save.
- **No false starting state offline.** A cache miss for a missing document is not read as a brand-new
  account: the listener runs with metadata changes, drops a from-cache non-existent snapshot, and emits
  nothing until the server answers. A consumer shows its loading state rather than Level 1.
- **One configuration per value.** Each emission reads the cached XP configuration once and uses it for
  both the replay and the Level threshold. A configuration refreshed while a screen is open applies
  from the next emission. (Relaxed on 2026-10-22, below.)
- **Sign-out ends the flow.** Permission denied completes the listener, and the flow completes with no
  further value, cancelling a replay still in flight.

Further accepted inaccuracy:

- **One repeated value before sign-out completes.** Between sign-out and the permission-denied
  teardown, a queue change can re-emit the same User's last value once. The screen is already leaving.
- **Two screens can briefly disagree.** Each collector has its own listener and replays the same
  snapshots, so two open screens (Home under the Summary) differ only while one is mid-replay.

**2026-10-10 — Delivered sessions are applied once.** `user-stats` lists the sessions applied to it,
an applied Pending Session adds to no aggregate, the worker removes an entry only after every refresh
read succeeds, and the Summary's baseline excludes its own session. This resolves the under-count after
delivery, the double count after a crash and the double count between refresh and removal. See
[ADR-0061](0061-delivered-sessions-applied-once-in-the-projection.md).

**2026-10-11 — The Summary's Level card follows its score.** A scored Summary draws its Level card
from the session's score; only a Summary with no score follows the live Level stream. Accepted: a
scored card can differ from Home's when the server scored with a newer XP configuration than the
device's cached one, or when the server scores the session differently from the Summary. The Summary
never replaces a score it has shown.

**2026-10-22 — One live scoring state stream.** The live flow moved into `ScoringStateRepository`
(`observeScoringState`, served by `DefaultScoringStateRepository`) and emits the whole projected
`ScoringState`, Streak and Daily Goal fields included, instead of `LevelProgress`. Every rule of the
2026-10-10 amendment holds, except that the Level threshold is no longer measured inside the
repository: `ObserveScoringStateUseCase` measures it, and reads the Streak and today's studied seconds
against today's local date. The repository still emits only on change, so a delivery that moves a
session from the queue to the server state emits nothing.

- **Two configuration reads per value.** The replay and the use case's Level threshold each read the
  cached XP configuration, so a refresh between the two reads can briefly measure a value against a
  newer Level curve than its replay used. Accepted: the configuration rarely changes after release,
  and the next emission corrects it.
- **No midnight re-emission.** Today is read on each upstream value only, so a Streak at risk or
  today's minutes stay as they were across midnight until the scoring state next changes.

**2026-10-26 — The Summary always shows the local score.** The Summary no longer waits for the
server's score: the wait ran out on most sessions. The worker publishes no delivery report, and
`submitStudySession` answers with nothing. See the Decision section and Considered Options above.

**2026-10-26 — The Summary's score reads only the cache.** A first session in a Subcategory, finished
offline, showed no score: the default read of its missing Card Progress document failed offline. The
score's baseline now reads the Firestore cache only, and a Card Progress document the cache holds
nothing for reads as no record. See the Decision section and Considered Options above.
