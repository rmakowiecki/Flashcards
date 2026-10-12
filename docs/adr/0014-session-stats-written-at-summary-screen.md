# Session results are committed once, at the Session Summary screen

> Status: superseded ([ADR-0049](0049-server-authoritative-session-commit.md)) for
> everything downstream of the write path; this document's collection layout and field shapes remain
> current.

> **Superseded in part** ([ADR-0049](0049-server-authoritative-session-commit.md)): the
> single client-issued commit batch/transaction described below — `CommitStudySessionUseCase` and
> `StudySessionRemoteDataSource`'s commit methods — is removed. A server-authoritative `submitStudySession`
> Cloud Function becomes the sole writer of `sessions/{sessionId}`, the packed per-Subcategory progress
> documents, `progress/summary` and `progress/user-stats`, inside its own Firestore transaction; the
> client only submits what happened and shows an optimistic, non-authoritative preview. What stands:
> this document's collection layout and field shapes (`sessions/{sessionId}`'s own document shape,
> `progress/summary`, `progress/user-stats`, the per-Subcategory singleton path) — the newer design relocates who
> writes them, not what they look like.
>
> **Further extended** ([ADR-0048](0048-streak-and-daily-goal-ride-the-session-payload.md)):
> `sessions/{sessionId}` gains one more field beyond what either this document or the newer design
> lists, `studyDate` (`yyyy-MM-dd`, the session's local calendar day) — needed for server-side
> streak/daily-goal evaluation. Submitted alongside everything else in the payload, but no longer
> trusted as-is: the server derives its own authoritative value from the session's start timestamp and
> a client-reported UTC offset (ADR-0049).

## Decision

### Nothing is written during a session

No Firestore write occurs while a Study Session runs — not per card, not per Rating, not per voice
grade. The session accumulates its outcome in memory and hands it forward.

### The Session Result carries its own card results

One collection holds every Study Session, Rated and Fast alike:
`users/{uid}/sessions/{sessionId}`.

**The document's shape depends on its own `studyMode` field.** Rated and Fast share most fields, but
a Rated card has Ratings, Attempts and a Terminal State and a Fast card has none of that — only
`Seen`. Rather than giving every Fast document zeroed-out mastery counters and every Fast card entry
a meaningless `attemptsUsed: 0`, the fields that only apply to Rated are simply absent from a Fast
document. A reader decides which shape to expect from `studyMode` alone — no second discriminant is
stored anywhere, because a session has exactly one Study Mode for its whole life, so every entry in
one session's `cardResults` is already known to be the same variant as the document itself.

```text
// Shared by both Study Modes
sessionId: String
startTimestamp: Timestamp
durationSeconds: Int
studyMode: RATED | FAST
isAbandoned: Boolean
categoryId: String
categoryName: String            // denormalized
subcategoryIds: List<String>
subcategoryNames: List<String>  // denormalized
cardCount: Int
newCardsStudied: Int            // both modes can study a card for the first time
cardResults: {                  // keyed by cardId
  <cardId>: {
    subcategoryId: String
    state: Mastered | Partial | Failed   // RATED documents only
    state: Seen                          // FAST documents only — the one value a Fast card can have
    attemptsUsed: Int                    // RATED entries only
    wasPreviouslyMastered: Boolean       // RATED entries only
  }
}

// RATED documents only — Fast has no Terminal States to tally
cardsMastered: Int
cardsPartial: Int
cardsDefended: Int
cardsDemastered: Int
```

**One session is one document.** The per-card results are embedded, not held in a subcollection.

**`newCardsStudied` is the one counter both variants keep.** Coverage — a card being **Studied** for
the first time — is not a Rated-only idea: a Fast card seen for the first time is just as much a new
card as a Rated one, and the "genuinely new card" scoring bonus reads for both modes alike. It
is only *mastery* (`cardsMastered`/`cardsPartial`/`cardsDefended`/`cardsDemastered`, and the
Terminal-State-derived `state` values above) that Fast has no concept of.

> **Scope note:** `newCardsStudied`, `cardsDefended` and `cardsDemastered` are all present in the
> schema above from the PR that introduces this collection, but not all computed for real from day
> one. `newCardsStudied` is written as zero until the PR that adds card-progress persistence, which is
> what reads prior state and can tell a new card from a returning one — the field exists from the
> start so no later PR has to add a column, but the value is a placeholder until that read exists.
> `cardsDefended`/`cardsDemastered` are written as zero the same way, until the PR that adds Mastery
> Defense actually produces a defended or de-mastered card. Neither is the omission this ADR describes
> above for a *Fast* document — that omission is permanent and structural (Fast has no mastery to
> report, ever); this is a temporary zero on a field that will hold real Rated data once its
> producing PR lands. The commit batch itself grows by one further write only once a later PR adds the
> account-wide scoring state — see "The commit is one batch" below. Which of these compute for real at
> any given point is a property of which PRs have landed, not of this ADR — check the current
> implementation tickets, not this document alone, for what is true today.

The names are denormalized so a Recents card renders from a single `orderBy(startTimestamp,
DESCENDING).limit(n)` query with no joins. That query's cost is the limit, not the collection size,
so the collection may grow without bound.

The per-outcome counts are stored alongside `cardResults` rather than derived from it on read, so a
session's scoring breakdown is reproducible from the record without walking every entry.

### Why `cardResults` is embedded

Firestore bills **per document read**, not per byte. A Recents carousel showing ten sessions costs
ten reads whether those documents are slim or fat, so splitting `cardResults` into a subcollection
buys no read saving at all — it only halves the bytes on the wire, at the cost of doubling the writes
on every commit and adding a second fetch whenever a past session is opened.

Size is bounded by construction: one entry per distinct card, and a session's length is capped at
`StudySessionConfig.MAX_LENGTH`. Even a full 50-card session is a handful of scalar fields per entry,
nowhere near Firestore's 1 MiB document limit.

**No transcript is persisted, anywhere.** A voice-answered card's sanitized transcript is surfaced
only transiently, on screen during the Rated session itself, to show the user what was heard before
grading it. Once the card is graded, only the resulting `state` and `attemptsUsed` carry forward into
`cardResults` — the spoken content itself is not retained in Firestore, in the session ViewModel's
result, or on the Summary route. Nothing today reads a transcript back after the session ends; adding
persistence for a hypothetical future revisit feature is deferred until that feature is actually
designed (see `docs/design/premium-voice-grading-pipeline.md`).

The one real cost is that the Android client SDK has no field projection, so Recents transfers
`cardResults` it never renders. If that ever measures badly, the fix is a separate slim index
document — an optimisation to make when it is needed, not a reason to pay two writes per session
forever.

### The commit is one batch, at the Summary screen

The Session Summary screen computes the XP breakdown from the session result and the `XpConfig`
snapshot ([ADR-0047](0047-xp-values-behind-a-config-repository.md)), then performs a **single
batched write**:

- the `sessions/{sessionId}` document, `cardResults` included
- `progress/details/subcategories/{subcategoryId}` — one packed progress document per Subcategory
  touched ([ADR-0016](0016-card-progress-model.md))
- `users/{uid}/progress/summary` — nested-key counter increments
- `users/{uid}/progress/user-stats` — `xp`, `level`, `xpIntoCurrentLevel`, `currentStreak`,
  `bestStreak`, `lastStudyDate`, `goalMetDate`

**A single-Subcategory session therefore commits four writes**, whatever its length: session,
progress, summary, progression. A composite session commits one further `progress` write per
additional Subcategory touched — three fixed writes plus one per Subcategory.

**This four-write count is the end state.** The PR that introduces this collection builds only the
first three of these four (session, progress, summary); `progress/user-stats` does not exist until a
later PR adds it. A single-Subcategory session is three writes until then, not four. See the scope
note above.

**This batch is atomic but not transactional.** It commits or fails as a unit, but it does not
re-read `progress/details/subcategories/{subcategoryId}` or `progress/user-stats` at commit time, so two sessions racing on the same
Subcategory can both read the same starting state and both apply their increments, double-counting
`studiedCount`, mastery deltas and XP. This is an accepted limitation for a single-account project —
a transactional, idempotency-checked commit is future work if genuine multi-device concurrency ever
needs to be supported.

**The commit is guarded against being applied twice for the same session.** Unlike the
cross-session race above, a duplicate commit of the *same* `sessionId` — a retried write after a
dropped response, say — is straightforward to prevent: the batch includes a create-only write
(`create()`, which fails if the document already exists) for `sessions/{sessionId}` itself. If that
document already exists, the whole batch fails and nothing is double-applied. This needs no
transaction, only that the session document's write in the batch uses `create` rather than `set`.

### Per-User singletons live in the `progress` collection

Both the progress summary and the scoring state are one document per User. Firestore paths alternate
collection and document, so each needs a fixed document id inside a collection:

```text
users/{uid}/progress/summary
users/{uid}/progress/user-stats
```

One security rule covers the collection, and a future singleton needs no new rule.

**The packed per-Subcategory progress documents ([ADR-0016](0016-card-progress-model.md)) are not
siblings of these singletons**, even though they live under the same `progress` collection.
Firestore cannot nest a collection directly inside another collection, so those documents sit one
hop deeper, under a fixed `details` anchor document: `progress/details/subcategories/{subcategoryId}`.
`progress` itself therefore holds only per-User singleton documents — `summary`, `user-stats`, and
the anchor `details` — keeping the collection's own layer uniform.

**Scoring state does not live on `users/{uid}` itself.** Entitlement is not a field on that
document — it is the separate subcollection `users/{uid}/entitlement/premium`
(`functions/src/lib/entitlement.ts`), written only by the Admin SDK and read server-side by the
premium Cloud Function; that subcollection stays default-denied regardless of any rule granted on
the parent document, so making `users/{uid}` client-writable would not by itself expose it. The real
reason is simpler separation of concerns: `users/{uid}` is reserved for identity and admin-managed
data, and a session commit should touch exactly the documents scoring needs and nothing that isn't
scoring. A separate client-owned document under `progress/` keeps that boundary clean and costs the
same single write.

The summary and the scoring state stay **two** documents rather than one. The summary is a maintained
rollup that may need self-healing by recounting the packed progress documents and overwriting; the
scoring state is authoritative and derivable from nothing. Merging them would let a self-heal path
clobber a User's XP. One batch, one atomic commit. A session is either fully recorded or not
recorded at all.

The Summary screen is the mandatory exit path for every session, partial included: deck end and
exit-confirmation both route to it.

### How the result reaches the Summary screen

The Summary route carries the whole session result as route arguments, flattened into primitives and
lists of primitives the same way `StudySessionRoute` already flattens `VoiceSettings` and
`IntRange` — `androidx.navigation`'s typesafe routes only derive a `NavType` for primitives, enums
and lists of those. `cardResults` becomes one parallel list per field, indexed together: `cardIds`,
`subcategoryIds` and `states` are always present, for both modes. `attemptsUsed` and
`wasPreviouslyMastered` are Rated-only — mirroring the document shape above, these two lists are
`null` for a Fast route, not lists of zeroes and falses for cards that have neither concept. The
route's own `studyMode` argument is what tells the Summary which shape to expect, same as the
document. No transcript field, since none is persisted (see above). A **past** session's detail view
instead carries only `sessionId`, and that separate screen reads
`sessions/{sessionId}` back from Firestore — one document, everything included — rather than
receiving `cardResults` through the route.

The session ViewModel therefore does not commit. It seals `cardResults`, stamps `durationSeconds`, and
navigates to the Summary with the flattened result as route arguments.

## Context

A full-length Rated session writing each outcome as it happens is up to `StudySessionConfig.MAX_LENGTH`
(50) Firestore writes, which does not scale across users. Worse, it has no clean boundary: a user
exiting mid-session leaves half their progress in Firestore with nothing recording that the session
was cut short. The session ViewModel already holds every outcome in memory, so deferring costs
nothing.

Deferring raises the question of *how far*. Two candidates: commit when the session terminates, then
show a Summary that reads back what was written; or carry the result to the Summary and commit
there. The XP breakdown decides it. XP is computed at the Summary — it is the screen that animates
it line by line — and whoever computes XP must persist it. Splitting the two means two writes and a
window in which a session document exists with no XP applied to the user.

It also raises *how many documents*. The original shape here — a slim parent plus a per-card
`outcomes` subcollection, alongside a per-card progress collection — cost around a hundred writes
for a full session. Since Firestore's billed unit is the operation, that number is the one that has
to come down, and both halves of it come down by packing: `cardResults` into its session document, and
card progress into one document per Subcategory.

The result-handoff question is separate, and constrained by the navigation library.
`androidx.navigation` derives a `NavType` only for primitives, enums and lists of primitives, which
this codebase already discovered and worked around by flattening `VoiceSettings` and `IntRange` into
primitive route fields. `cardResults` follows the same convention: one parallel list per
field, all indexed together, rather than one JSON blob. A session is capped at
`StudySessionConfig.MAX_LENGTH` (50 cards), so the flattened lists stay small — nowhere near the
route string's practical size limits — and the route needs no custom `NavType`.

## Alternatives considered

**Incremental per-rating writes** — rejected on write volume and on partial-session ambiguity, as
above.

**Commit at termination; Summary reads it back** — rejected. It splits XP computation from XP
persistence, needs two writes, and leaves a window where a session is recorded but its XP is not.

**A per-card `outcomes` subcollection under the session** — rejected on operation cost. It was the
original decision here, justified by keeping the Recents carousel's reads small. That justification
does not survive scrutiny: reads are billed per document, so Recents pays the same ten reads either
way. The subcollection only ever saved bandwidth, and it charged a write per card to do it.

**A single `outcomes` detail document as a child of the session** — rejected. It keeps Recents slim
and bounds the write count at two, which is defensible, but it still costs one extra write per
session and one extra read whenever a past session is opened, to save bytes on a screen whose reads
are already cached.

**Separate `recentSessions` and `sessions` collections**, one denormalized for Home and one detailed
for stats — rejected. Two documents per session that must agree, written from the same batch,
differing only in which fields they carry.

**An `@ActivityRetainedScoped` holder, written by the session ViewModel and read once by the Summary
ViewModel** — rejected. It keeps the route to a bare `sessionId`, but it makes the Summary
ViewModel's state depend on a side channel outside the navigation contract, and its retained scope
outlives what the Summary screen actually needs (it survives configuration change for as long as the
hosting Activity does, not just for the Summary's lifetime). Passing the result as route arguments
keeps state visible in the one place — the back stack — that already has to represent it.

**Custom `CollectionNavType` carrying all of `cardResults` as one JSON blob in the route** —
rejected in favor of flattening. It works and sizes fine, but it breaks the flattening convention
this codebase already settled on for `VoiceSettings` and `IntRange`, trading one custom serializer
for what parallel primitive lists already do natively.

**A bare `data object StudySummaryRoute` with the fresh result read from some other source** —
rejected for the same reason as the `@ActivityRetainedScoped` holder above: the fresh result would
still need a side channel outside the navigation contract to reach the screen, just under a different
name. *(An earlier version of this rationale also argued this shape blocks a future past-session
detail view from sharing the same route. That turned out not to be the plan: the Summary route is
fresh-session egress only, and a past-session detail view, if built, is a separate screen and route
entirely — see "How the result reaches the Summary screen" above. The route-shape decision itself is
unchanged; only this piece of the reasoning for it was corrected.)*

## Consequences

- A session commit is a small, bounded number of writes — four for the common single-Subcategory
  case in the end state, three until the account-wide scoring state exists — independent of how many
  cards were studied.
- Opening a past session's detail costs **one** read: the session document carries its own
  `cardResults`.
- Home's Recents transfers `cardResults` data it does not render. Bounded at roughly 13 KB per
  session and served from Firestore's on-device cache after first load; revisit only if measured.
- If the app is killed while the Summary screen is showing, the session is lost entirely. This is
  the same exposure as a mid-session crash and is accepted; a future mitigation could persist the
  in-progress `cardResults` to DataStore and recover on next launch.
- `users/{uid}/progress/user-stats` carries `lastStudyDate` and `goalMetDate`. Streak continuation and
  the once-per-day daily-goal award are both uncomputable without them — the first needs to know
  whether a session today has already been counted, the second whether today's goal was already met.
  Both are local calendar dates stored as `yyyy-MM-dd` strings rather than Timestamps: they are
  calendar days, not instants, and the only questions asked of them are same-day and later-day.
- `users/{uid}` stays admin-only, unreadable and unwritable by the client, exactly as it is today.
- Day attribution uses the session's **start** timestamp, not the commit time, so a session that
  crosses midnight counts toward the day it began.
- The Summary ViewModel has exactly **one** load path — the fresh result from route arguments. It is
  never used to view a past session; a past session's detail view, if built later, is a separate
  screen and route, reading `sessions/{sessionId}` back from Firestore on its own, not through this
  ViewModel.
- The route arguments carry the whole fresh-result payload, so the Summary needs nothing beyond what
  navigation already hands it, and a process death that survives via `SavedStateHandle` restores the
  same arguments rather than losing the result. Nothing sensitive rides in that payload: no transcript
  is ever part of the result, so `SavedStateHandle`'s disk-backed persistence carries only card ids,
  states and counts.

## Amendments

**2026-10-03 — Home's Recents no longer query `sessions`.** They read the server-written
`users/{uid}/recents/state` document instead ([ADR-0057](0057-recents-state-projection.md)). The
denormalized names on the session document stay, and Recents carry the same names: each `recents/state`
entry stores its Category and Subcategory names, so a row shows and replays without a taxonomy read.
`cardResults` stays embedded on its own merit, since no reader needs a session without its cards.
