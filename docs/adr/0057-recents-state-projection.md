# Home's Recents read one server-written `recents/state` document

> Status: accepted

## Context

Home shows a live list of the User's latest Study Sessions. Session documents
(`users/{uid}/sessions/{sessionId}`) hold everything, but Firestore bills one read per document
returned, so a `sessions` query on every Home visit would cost one read per row per visit, and a
listener would cost another read per row on each re-attach. The list is short and changes only when a
session is delivered, which already happens inside one server transaction ([ADR-0049](0049-server-authoritative-session-commit.md)).

## Decision

### One document, written in the session's own transaction

Each User has one `users/{uid}/recents/state` document. `submitStudySession` writes it in the same
transaction that writes the session document, so the two never disagree. Clients may only read it.
The full shape is in [firestore-schema.md](../design/firestore-schema.md#recents).

### A map keyed by `sessionId`, trimmed by start time

`entries` is a map keyed by `sessionId`, like `favorites/state`. A retried submission finds its id
already present and leaves the document untouched, so idempotency needs no scan. After an add, the
map keeps the 15 newest entries by `startTimestamp`, ties evicting the lower `sessionId` first.
Ordering never depends on delivery order, so a Pending Session delivered late lands where it started
and never pushes out a newer one.

### Names stored; the Category is looked up only for styling

An entry repeats its session's ids and names, Study Mode, delivery flag, source type, card count,
duration and `xpTotal`. Category and Subcategory names never change and are never deleted, so a stored
name is always right, and a row never needs a taxonomy read to show or to replay. Pending Sessions
carry the same names. `ObserveRecentSessionsUseCase` looks up each Category only for its color and
icon, as Favorites does. A Category that cannot be read leaves the row without them; the row is never
dropped.

### Pending Sessions are merged on the device

`DefaultRecentSessionsRepository` merges the document with the User's Pending Sessions, scored with
preview XP ([ADR-0055](0055-pending-sessions-and-local-progress-projection.md)). A server entry wins
over a Pending Session with the same id, Pending Sessions with no Flashcard Results are dropped, and
the merged list is capped at 15 after sorting.

## Alternatives considered

- **A `sessions` query ordered by `startTimestamp`.** Rejected on cost: one read per row per visit,
  against one read on attach and one per change.
- **An array of entries instead of a map.** Rejected: idempotency would need a manual scan for the
  session id, and the shape would differ from `favorites/state`.
- **Ids only, with names resolved live.** Rejected: offline, a Category or Subcategory that was never
  cached would drop the row or replay a smaller session, although nothing was renamed or deleted.
- **Category color and icon stored too.** Rejected: Pending Sessions do not carry them, so a lookup
  is needed anyway, and repeating each icon in every entry costs bytes for no gain.

## Consequences

- Home's Recents cost one read when the listener attaches and one per delivered session.
- The document is a projection: it can be rebuilt from `sessions/*` at any time.
- Every row shows offline, with the names it was studied under. A row whose Category was never
  cached shows without its color and icon.
- Replay sends the stored Subcategories as they are; Preview no longer checks whether they exist.
- When a session is delivered, its queue entry can be removed before the snapshot with its server
  entry arrives, so its row can briefly disappear and come back. Accepted.
- A Recent row opens Preview rather than browsing, an exception recorded in
  [ADR-0041](0041-topic-row-tap-browses-play-button-studies.md).
