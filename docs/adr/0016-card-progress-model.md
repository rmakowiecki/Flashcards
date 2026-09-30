# The card progress model: packed per-Subcategory progress, two derived sets, one summary document

## Decision

### Progress is packed per Subcategory, not per card

A User's per-card progress lives in one document per Subcategory:

```
users/{uid}/progress/details/subcategories/{subcategoryId}

categoryId: String                  // denormalized, so a document identifies its own scope
cards: {                            // keyed by cardId
  <cardId>: {
    state: Seen | Failed | Partial | Mastered
    firstStudiedAt: Timestamp       // write-once
    masteredAt: Timestamp?          // most recent mastery, null if never, retained after de-mastery
  }
}
```

Only cards the User has actually studied appear in `cards`. This is the same packing idiom
[ADR-0037](0037-flashcard-content-sharded-by-byte-budget.md) already applies to flashcard content: one document
holding many entries, rather than one document per entry.

Two sets are derived from this one map:

- **Studied** — the card's key is present. Monotonic: a key is never removed.
- **Mastered** — the entry's `state` is `Mastered`. Mutable: de-mastery moves `state` down, it does
  not remove the key.

Mastered is therefore a subset of Studied structurally, not by convention.

### Why packed

Operations, not bytes, are what Firestore bills and what has to scale. A document per card makes a
50-card session cost 50 progress writes, and makes every screen that wants a Subcategory's progress
issue a query whose cost grows with how much the User has studied.

Packed, the same session costs **one** progress write per Subcategory it touched — constant in card
count — and every read of a Subcategory's progress is a single document read, constant in how much
was studied.

Size is not a constraint. An entry is roughly 100 bytes, so a Subcategory in which a User has
studied 500 cards is about 50 KB against Firestore's 1 MiB document limit. Nothing in the taxonomy
approaches the tens of thousands of studied cards it would take to threaten that.

Concurrency does not regress. Writes use `set` with merge on nested keys, which Firestore merges
entry by entry, so two devices touching different cards in the same Subcategory merge cleanly. Two
devices touching the *same* card is last-write-wins, exactly as it was per-document.

### What each Study Mode writes

**Rated** sessions write a card's Terminal State ([ADR-0044](0044-three-valued-terminal-state.md))
into its entry, and set `firstStudiedAt` on first contact. A card is Studied once it has completed
at least one Attempt.

**Fast** sessions create an entry with `state = Seen` **if and only if no entry exists**, and never
modify an existing one. A card is Studied once **its answer has been shown** — the player presenting the answer
under read-aloud, `isAnswerRevealed` set under manual advance. Reaching a card's question
and skipping forward marks nothing.

The create-if-absent rule is what makes this safe: a Fast session can never overwrite a judgement a
Rated session made, so re-listening to a mastered card cannot downgrade it.

Fast gains nothing else. No Ratings, no Attempts, no Terminal States, no Persistent Mastery, no
Mastery Defense, no shield icon.

### Mastery Defense stays Rated-only

**Mastery Defense** — guaranteeing a floor of previously mastered cards in a session so mastery is
re-tested rather than assumed, with a bonus for holding it and a penalty for losing it — applies to
Rated sessions exclusively. Fast has no
Rating step, so there is no outcome to defend with.

De-mastery fires on Terminal **Failed** only. A defended card ending Terminal Partial keeps its
mastery and earns neither the bonus nor the penalty.

### Private Flashcards are excluded entirely

A Private Flashcard never receives a progress entry, never enters Studied or Mastered, and never
earns card-level XP. Session-level XP — time studied, session completion, streak, daily goal — is
unaffected.

The reason is not purity but exploitability: new-card XP on a user-authored card is trivially
farmable. Create fifty private cards, run one Fast session, collect fifty first-seen awards; master
them, collect fifty masteries.

### One summary document for every ring

Category Details draws a ring for every Subcategory in a Category — thirteen in the Android case —
on a screen Users open constantly. Reading thirteen packed documents to count entries would be
thirteen reads and several hundred kilobytes for a heavy User.

So a single document per User holds the rollup:

```
users/{uid}/progress/summary

subcategories: {                    // keyed by subcategoryId
  <subcategoryId>: { masteredCount: Int, studiedCount: Int }
}
```

**One read serves every ring on the screen**, whatever the Category, and the Home screen's progress
displays read the same document.

| Screen | Reads |
|---|---|
| Category Details (a ring per Subcategory), Home progress | `progress/summary` — **one document** |
| Subcategory Details (per-card filtering), Preview (defense selection), Study Session (new-card and defense accounting) | the packed `progress/details/subcategories/{subcategoryId}` document for each Subcategory in scope |

Counter deltas are applied as nested-key `FieldValue.increment`s inside the single session-commit
batch ([ADR-0014](0014-session-stats-written-at-summary-screen.md)), never separately.

### The ring denominator is already in the taxonomy

`Subcategory.cardCount` exists today, is maintained by the seed tooling, and is read by every screen
that lists Subcategories — including Category Details. The denominator therefore costs no read at
all, and is not duplicated onto any progress document.

## Context

Three questions had to be answered together, because each constrains the others' storage shape.

**What does progress mean for a User who only studies in Fast mode?** Fast has no Rating step: TTS
reads the question, pauses, reads the answer, advances. Inferring mastery from listening is not
meaningful, and a mid-session tap to self-rate while TTS runs breaks the passive model that is the
whole point of the mode. But recording *nothing* means a Fast-only User's progress rings read zero
forever no matter how much they study, and the app gives them no hint that their study mode is the
reason.

The resolution is that **coverage and quality are different claims**. `Seen` is not a weak mastery
signal, it is the absence of a quality judgement — something Fast can assert truthfully and cheaply.
Mastery inference remains off-limits to Fast; coverage does not.

**How are two differently-mutable sets over the same cards stored?** Studied is monotonic, Mastered
is not. Modelling each separately means two sources of truth for one card, an unenforceable subset
invariant, and double the writes.

**What does this cost per session, and per screen open?** This is the question that decided the
shape. A document-per-card model is the obvious one and the expensive one: a full session commits
around a hundred writes, and it grows with session length. Since Firestore bills per operation, and
since every read of a Subcategory's progress wants the whole slice anyway, packing collapses both
sides at once.

## Alternatives considered

**A document per card, `cardProgress/{cardId}`** — rejected on operation cost. It was the original
decision here. A 50-card session commits 50 progress writes on top of the session itself, and a
Subcategory's slice costs a query whose read count grows with study history. The packed form makes
both constant. Its one genuine advantage — a `where state == Mastered` query across all
Subcategories at once — is not something any screen in the design asks for.

**Per-Subcategory counter documents** (`subcategoryProgress/{subcategoryId}`) — rejected once
progress was packed. Their entire justification was avoiding N per-card reads per screen open, and
packing already does that: Category Details would read thirteen documents either way, so the
counters saved **zero** operations while adding one write per commit and a number maintained
alongside the truth it summarises. A single summary document does what they were meant to
do and actually reduces reads, from one per Subcategory to one per screen.

**No summary at all, counting entries in the packed documents** — rejected. It makes drift
structurally impossible, which is genuinely attractive, but Category Details would then transfer
every studied card in the Category to draw thirteen rings.

**Duplicating a `globalCardCount` onto a progress document** — rejected as redundant. It existed to
save a second read per Subcategory for the ring denominator, but `Subcategory.cardCount` is already
loaded by the screens that draw rings.

**Fast records nothing; Fast-only Users get no rings** — rejected. It makes the progress rings
punish a legitimate study mode, silently.

**Fast infers mastery from listening, or offers a mid-session self-rate while TTS runs** — rejected,
and this is the decision that has not changed. The first fabricates a signal; the second destroys
the passive-listening model. Showing mastered cards as visually distinct in Fast without any
interaction model was rejected too — a shield icon with no actionable meaning only confuses.

**Fast marks the whole selected pool as Seen at session start** — rejected. A User exiting after
three cards would bank coverage for a deck they never saw, and Studied would stop meaning anything.

**Fast marks a card Seen when its question appears** — rejected. Fast's transport controls make
skipping cheap and common; a question that flashed past taught nothing. In this mode the answer is
the study event.

**Fast requires the card to be fully completed and advanced past** — rejected as too strict at the
boundary: it silently drops the last card of every session a User exits on, which is the card they
most recently studied.

**Removing a card's entry on de-mastery** — rejected. It makes Studied non-monotonic, so a User who
mastered a card and later failed it watches their coverage ring go backwards for a card they
demonstrably studied.

**Including Private Flashcards, excluded from Mastered but counted in the ring denominator** —
rejected. The Mastered ring could then never reach 100% for any Subcategory the User has added a
private card to, showing a permanent ceiling with no visible cause.

## Consequences

- A session commit performs **one** progress write per Subcategory it touched, plus one summary
  write — regardless of how many cards were studied.
- Fast sessions perform progress writes, which they previously did not. The write is one per
  Subcategory and lands in the single commit batch rather than during the session.
- A Fast session's state must track which cards reached the answer — one boolean set it did not
  previously need.
- A Rated session's per-card state must retain the **best Rating so far**; see ADR-0044.
- The Firestore `whereIn` 30-id cap no longer applies to any progress read. A session reads one
  document per Subcategory in scope, whatever its length.
- `CardProgressRepository` exposes both read shapes — the packed Subcategory document and the
  summary — and serves three screens, so it belongs in `core:domain` rather than inside
  `feature:study`.
- The summary is a per-User singleton and therefore lives at a fixed document id (`summary`)
  inside the `progress` collection, alongside the scoring state ([ADR-0014](0014-session-stats-written-at-summary-screen.md)).
  A path like `users/{uid}/progressSummary` names a *collection*, not a document.
- `progress` holds only per-User singleton documents (`summary`, and `user-stats` once
  ADR-0014's scoring state exists) — the packed per-Subcategory documents above are not siblings
  of those singletons. Firestore cannot nest a collection directly inside another collection, so
  `progress/details` is a fixed anchor document with no fields of its own, existing only to host
  the real `subcategories` subcollection each packed document lives in.
- A Subcategory's ring denominator is its **global** card count, taken from the taxonomy. The
  card-count label printed beside a ring must use the same figure, or the percentage cannot be
  reconciled against the number next to it.
- A User who studies only Private cards sees rings that never move. Their Progress screen still
  advances on time studied, streak and daily goal.
- Drift is impossible between a card's state and the Studied/Mastered sets, since both derive from
  the same map. The summary can still drift from the packed documents; recomputing it is a read of
  the User's progress documents and one write, rather than a per-Subcategory aggregation sweep.
- A User who studies exclusively in Fast mode still never gains or loses Persistent Mastery, and
  their XP growth stays slower than a Rated User's — card mastery and defense are the larger awards.
  Only their coverage now advances.
