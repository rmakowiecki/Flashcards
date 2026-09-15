# Card Progress and Persistent Mastery

**Status: SUPERSEDED.** This design shipped as [ADR-0016](../adr/0016-card-progress-model.md) —
`users/{uid}/progress/details/subcategories/{subcategoryId}` and `progress/summary` are live, see
[firestore-schema.md](firestore-schema.md). This doc is kept only
as historical design rationale; do not treat its "not yet implemented" framing below as current.

## Overview

A cross-session record of what a User has done with each Flashcard. It carries two derived sets:

- **Studied** — every Flashcard the User has ever had in front of them. Monotonic.
- **Persistent Mastery** — the Flashcards they currently hold mastery on. Mutable.

Mastered is a subset of Studied by construction. Both are read from one place, and both drive the
progress rings on Category Details and Subcategory Details.

**Both Study Modes contribute to Studied.** A Rated Study Session records a Flashcard's Terminal
State; a Fast Study Session records only that the Flashcard was seen. Mastery itself — gaining it,
defending it, losing it — remains Rated-only. See [ADR-0016](../adr/0016-card-progress-model.md).

Two documents work together: a **packed progress document per Subcategory** (per-card truth) and a
single **progress summary** document per User (rollup counts, for cheap ring reads).

## Firestore structure

### Packed progress, one document per Subcategory

```
users/{uid}/progress/details/subcategories/{subcategoryId}

categoryId: String
cards: {                       // keyed by cardId; only studied cards appear
  <cardId>: {
    state: Seen | Failed | Partial | Mastered
    firstStudiedAt: Timestamp  // write-once
    masteredAt: Timestamp?     // most recent mastery, null if never, retained after de-mastery
  }
}
```

A Flashcard is **Studied** iff its key is present in `cards`. It is in **Persistent Mastery** iff its
entry's `state` is `Mastered`. De-mastery moves `state` down; it never removes the key, so coverage
never regresses.

This is the same packing idiom [ADR-0037](../adr/0037-flashcard-content-sharded-by-byte-budget.md)
applies to flashcard content, and it is chosen for the same reason: Firestore bills per operation. A
50-card session commits **one** progress write per Subcategory it touched rather than one per card,
and any screen wanting a Subcategory's progress reads **one** document rather than issuing a query
whose cost grows with study history.

Size is comfortable. An entry is roughly 100 bytes, so 500 studied cards in a Subcategory is about
50 KB against Firestore's 1 MiB document limit.

Writes use `set` with merge on nested keys. Firestore merges nested maps entry by entry, so two
devices touching different cards in the same Subcategory merge cleanly; two devices touching the
same card is last-write-wins, exactly as a document-per-card model would be.

**Private Flashcards are excluded entirely** — they never receive an entry, never count toward either
set, and never earn card-level XP. New-card XP on a user-authored Flashcard would otherwise be
trivially farmable.

### Progress summary, one document per User

```
users/{uid}/progress/summary

subcategories: {               // keyed by subcategoryId
  <subcategoryId>: { masteredCount: Int, studiedCount: Int }
}
```

Category Details draws a ring for every Subcategory in a Category — thirteen for Android — on a
screen Users open constantly. This document answers all of them in **one read**, whatever the
Category, and the Home screen's progress displays read the same document.

There is no per-Subcategory counter document. Under packing it would save nothing: Category Details
would read thirteen documents either way. A single summary is the only shape that actually reduces
the read count.

### The ring denominator

Both ring percentages are `count / Subcategory.cardCount`.

`Subcategory.cardCount` already exists on the taxonomy, is maintained by the seed tooling, and is
already loaded by every screen that lists Subcategories — so the denominator costs no read and is
duplicated nowhere.

**The denominator excludes Private Flashcards, so the card count printed beside a ring must be the
same figure.** A label counting Private Flashcards next to a ring that does not produces a percentage
the user cannot reconcile against the number next to it.

## Lifecycle

**First studied (either mode).** A Flashcard with no entry gets one created, with `firstStudiedAt`
set and `state` set to the session's outcome — the Terminal State in a Rated session, `Seen` in a
Fast session. New-card XP is awarded once, here, for either mode.

A Rated Flashcard counts as studied once it has completed at least one Attempt. A Fast Flashcard
counts once its **answer has been shown** — `VoicePhase.Answer` entered under read-aloud,
`isAnswerRevealed` set under manual advance. Skipping past a question marks nothing.

**Mastery gained.** A Flashcard reaches a Correct Rating on any Attempt in a Rated Study Session
(Terminal State: Mastered). On session commit, `state` becomes `Mastered` and `masteredAt` is set.
The summary's `masteredCount` for that Subcategory increments by 1 in the same batch.

**Mastery lost (de-mastery).** A previously mastered Flashcard reaches a **Failed** Terminal State in
a Rated Study Session. On session commit, `state` moves to `Failed` and `masteredCount` decrements by
1 in the same batch. The entry survives, so `studiedCount` is unaffected.

**Mastery defended.** A previously mastered Flashcard receives a Correct Rating on any Attempt.
Mastery is retained; bonus XP awarded (see [XP & Leveling System](xp-leveling-system.md)). No counter
write occurs — state does not change, only XP does.

**Partial is mastery-neutral.** A previously mastered Flashcard ending on a **Partial** Terminal State
keeps `state == Mastered`. It earns neither the defense bonus nor the de-mastery penalty, and writes
no counter delta. Only an outright Failed de-masters.

**Fast never downgrades.** A Fast session writes `Seen` **only when no entry exists**. Re-listening to
a mastered Flashcard cannot move it backwards.

## Write mechanics

Everything below happens in the **same batch** as the session document
([ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md)), never separately, so the
summary cannot drift from the progress it summarises.

While the commit walks the session's `cardResults` it groups entries by Subcategory. Per Subcategory it
builds one nested-key merge — touching only the cards this session studied, leaving every other entry
in the document untouched — and accumulates two deltas:

- `masteredDelta`: +1 per newly mastered, −1 per de-mastered, 0 for defended / Partial / unchanged
- `studiedDelta`: +1 per Flashcard that had no entry before, 0 otherwise

One `progress/{subcategoryId}` write joins the batch per touched Subcategory — in practice one, since
a session is usually scoped to a single Subcategory — plus one `progress/summary` write carrying every
Subcategory's increments as nested-key `FieldValue.increment`s.

`FieldValue.increment()` on a missing field or document starts from 0 and creates it via merge, so
there is no initialisation step for a User's first studied Flashcard.

To compute `studiedDelta` and to know which cards were previously mastered, the commit reads the
progress document for each Subcategory in scope first. That is one read per Subcategory, and it is
the same document the session already read at start.

**This read is not transactional.** A concurrent session on the same Subcategory (another device, or
another tab) can read the same starting document and apply its own deltas from it, so both sessions'
increments land rather than one being computed against the other's result — an accepted limitation
for a single-account project, same as noted in [ADR-0014](../adr/0014-session-stats-written-at-summary-screen.md).

## Reading progress

- **Category Details** (all topics in one Category, rings for each): `progress/summary` — **one
  document**, regardless of how many Subcategories are shown. Both ring perspectives render from it.
- **Home progress displays**: the same single document.
- **Subcategory Details** (per-card filtering by Mastered / Studied / Unseen): the packed
  `progress/{subcategoryId}` document — one read.
- **Preview Study Session Screen**: the same document, to pick Mastery Defense candidates.
- **Study Session**: the same document for each Subcategory in scope, to know which Flashcards are new
  (for new-card XP) and which were previously mastered (for defense accounting). **The Firestore
  `whereIn` 30-id cap no longer applies to any progress read** — session length is irrelevant to the
  read count.
- **Search results** (topics matched across Categories): `progress/summary` — one document, from which
  the matched Subcategories' counts are picked out in memory.
- **Browse default list**: no progress read — it shows a topic *count* per Category, not per-topic
  percentages.

`shards` subcollections (ADR-0037) are never touched by any progress read.

## Consistency

Within a Subcategory, a card's state and the Studied/Mastered sets cannot disagree: both derive from
the same map.

The summary can still drift from the packed documents, since it is a maintained count rather than a
derived one. It is written atomically with the progress documents in the same batch, so normal app
usage will not cause drift; a manual console edit, a future migration or a bug could. No proactive
reconciliation for v1. If drift is suspected, self-heal by reading the User's progress documents,
counting each map, and overwriting the summary in one write.

## Mastery Defense selection

Mastered Flashcards are **never removed from the session pool**. They remain as eligible as anything else, and a session draw may contain them by chance. What Mastery Defense adds is a **floor**: the selection always tries to include some, so mastery is periodically re-tested rather than assumed.

Selection happens at Preview Study Session Screen time (Preview owns all card selection — ADR-0004).

Rules:
- Only Flashcards within the session's Category/Subcategory scope are eligible
- Only global Flashcards — Private Flashcards are never defense candidates and hold no progress record anyway
- The floor is **10% of the session's resolved length** — `min(configured Length, eligible pool size)`, the same shrink-on-small-pool rule Card Selection already applies when the pool can't fill the configured Length — rounded to the nearest whole card (half rounds up), minimum 0. If a natural draw already contains that many mastered Flashcards or more, nothing is added
- If it contains fewer, mastered Flashcards are swapped in for unmastered ones until the floor is met or the eligible mastered Flashcards run out
- **Every mastered Flashcard in the final selection is a Defense Flashcard** — not only those swapped in. Two identical mastered Flashcards in one session must not behave differently depending on how they were drawn
- The session is always exactly its resolved length, so the card count and estimated duration on the Preview screen stay truthful without any special accounting; it only equals the configured Length when the eligible pool is at least that large
- The combined selection is then ordered as normal
- The count of Defense Flashcards is **not shown** on the Preview screen — internal mechanic, transparent to the user

Because there is no exclusion, a User who has mastered most of a Subcategory still gets a full-length session; it is simply mostly re-testing. The degenerate "nothing left to study" case cannot arise.

**A future per-card progress filter** — the planned Mastered / Studied / Unseen chips — is the only thing that removes mastered Flashcards from the pool. When a User filters them out, the Defense floor is necessarily 0. That is a normal outcome, not an error, and the selection API is shaped to express it.

## Visual distinction in session

During a Rated Study Session, defense Flashcards are marked with a **small shield icon** alongside the question. Final visual treatment determined at UI implementation time.

The shield is the only signal — no label, no count, no explanation in the session UI. The XP breakdown on the Session Summary screen implicitly confirms defense was in play.

## Fast mode scope

Fast Study Sessions:
- Write progress entries with `state = Seen`, and only where none exists
- Contribute to **Studied** and to the summary's `studiedCount`
- Award new-card XP for Flashcards seen for the first time
- Do **not** insert defense candidates, display any mastery distinction, or change `state` on an existing entry
- Cannot gain or lose mastery

Mastery state is frozen during Fast sessions; coverage is not.
