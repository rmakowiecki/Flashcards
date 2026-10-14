# Progress figures are read from server-written documents, with no local statistics cache

## Decision

The Progress screen and the launcher widget do not read a locally cached set of statistics, and they do not recompute figures from raw session history. Every figure comes from a document the server already maintains for the User:

- Level, XP, Streak and the Daily Goal day total come from the scoring state, written by `submitStudySession`.
- Cards Studied and Cards Mastered come from the progress summary.
- Lifetime totals, minutes per day and minutes per Category come from an activity aggregate that `submitStudySession` also updates in the session's transaction.

Pending Sessions are projected onto these documents on the device, as they are for Card Progress and the scoring state ([ADR-0055](0055-pending-sessions-and-local-progress-projection.md)). Room and a DataStore statistics cache were both considered and rejected.

## Context

The first design cached pre-computed aggregates in DataStore on every session end and recomputed them from the `sessions` collection after a reinstall. That was written before sessions were committed by a server-authoritative function ([ADR-0049](0049-server-authoritative-session-commit.md)). Since then the scoring state is stored on the server and read live, so a cache would be a second copy that has to be kept consistent with it. Replaying the whole `sessions` collection after a reinstall would also read one large document per past session and could give a different XP answer once the XP configuration changed.

## Alternatives considered

**Room for raw session records** — rejected. It would only pay off if the device had to query raw sessions locally, and Firestore is the source of truth for them.

**A DataStore cache written on session end** — rejected. It duplicates the scoring state and the progress summary, needs its own rebuild path on a fresh install, and cannot include Pending Sessions without repeating the projection.

**Scanning `sessions` on each load of the Progress screen** — rejected. Cost grows with the User's history, and each document carries every card result.

## Consequences

- A reinstall or a new device needs no rebuild step: the server's documents already hold every figure.
- The activity aggregate is new server state. It is added to `submitStudySession` and to the Firestore schema when the Progress screen is built, and the rule that updates it is shared by the server and the device so the projection matches.
- Offline, the screen shows the last values Firestore's persistence holds, plus Pending Sessions.
- The launcher widget has no data store of its own either. At each refresh it reads the same repositories as the Progress screen, which answer from Firestore's offline cache and the Pending Session projection ([launcher-widget.md](../design/planned/launcher-widget.md)).
