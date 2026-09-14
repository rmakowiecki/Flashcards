# Firestore Schema

This is the single source of truth for the current Firestore data schema — don't reconstruct it from ADRs, which are a
decision history and not always kept in sync with the live shape.

## Taxonomy: categories and subcategories

```
// Global taxonomy (admin-defined)
categories/{categoryId}                               → { name, order, subcategoryCount, color?, iconSvg? }
subcategories/{categoryId-subSlug}                    → { name, categoryId, order, cardCount }
subcategories/{categoryId-subSlug}/shards/{n}         → { flashcards: { "<cardId>": { id, question,
                                                           answer, tags[], difficulty, createdAt,
                                                           questionCode?, answerCode?,
                                                           extendedContext?,
                                                           questionSpoken?, answerSpoken? }, ... } }
```

- **Strict 2-level taxonomy**: Categories and Subcategories are separate top-level collections. Subcategory IDs are namespaced `{categoryId}-{subSlug}` (e.g. `android-testing`) to guarantee uniqueness across parent categories. See [ADR-0001](../adr/0001-flat-two-level-taxonomy.md) and [ADR-0007](../adr/0007-firestore-collection-structure.md).
- **Cards live as a subcollection of their Subcategory** (`subcategories/{subcategoryId}/shards/`), packed into a small number of byte-budgeted shard docs (`{ flashcards: {cardId: {...}, ...} }`, a map keyed by card id — not an array, so a future admin curation-fix tool can dot-path-patch one card without touching any other) rather than one Firestore document per card. Fetching all Flashcards for a Subcategory is a single `getDocuments()` over `shards` — no WHERE clause, no index — followed by flattening each shard's `flashcards` map values client-side. No separate `cards/` collection. See [ADR-0007](../adr/0007-firestore-collection-structure.md) for the subcollection placement and [ADR-0037](../adr/0037-flashcard-content-sharded-by-byte-budget.md) for the shard-doc granularity.
- **`subcategoryId` is not stored on Flashcard documents** — it is encoded in the collection path. Each card's own `id` field, however, *is* stored inside its shard's `flashcards["<cardId>"]` entry (a shard doc's id is just its shard index, so it can no longer stand in for the card's id).
- **`difficulty` is a mandatory integer (1–10)** on every Flashcard document, global and Private alike. Global documents missing this field are filtered at the DTO layer and never reach the domain (a backfill concern for the pre-existing corpus); Private Flashcards have no such gap — the creation dialog's mandatory Slider means none can be saved without one. See [ADR-0010](../adr/0010-difficulty-field-design.md).
- **`extendedContext` is nullable** on global Flashcard documents. Omitted on simple cards (difficulty 1–3) where the Q&A is fully self-explanatory. Present and progressively richer as difficulty rises: mid cards (4–6) carry a concrete example or short snippet; hard/expert cards (7–10) carry fuller context — edge cases, cross-concept relationships, pitfalls. Never duplicates the `answer` field.
- **Tags are flat untyped strings** in `tags[]` on each Flashcard. No `tags/` collection. See [ADR-0006](../adr/0006-flat-denormalized-tags.md).
- **Category `iconSvg`**: inline plain SVG text (not a URL), rendered on-device via `androidsvg`. No Firebase Storage SDK dependency. Nullable alongside `color`, since the seed pipeline may auto-create a category before either is curated. See [category-icon-color.md](category-icon-color.md).

## Cache freshness signal

```
// Cache freshness signal (ADR-0039)
meta/seed                                             → { value: Int }  // monotonic, bumped by seed_firestore.py
```

## User identity and entitlement

```
// Per-user — admin-managed identity data only; scoring state lives under progress/, not here
users/{uid}                                           → {}  // no client-writable fields today
users/{uid}/entitlement/premium                       → { isPremium }  // Admin SDK only, functions/src/lib/entitlement.ts
```

- **Scoring state is deliberately NOT on `users/{uid}`.** Entitlement is a separate document, `users/{uid}/entitlement/premium` (`functions/src/lib/entitlement.ts`), written only by the Admin SDK and read server-side by the premium Cloud Function; that subcollection is default-denied regardless of any rule on the parent `users/{uid}` document. The real reason scoring state lives under `progress/` instead is separation of concerns, not privilege escalation: `users/{uid}` is reserved for identity/admin-managed data, while `progress/user-stats` is the one document a session commit needs to touch and nothing else. `dailyGoalMinutes` is likewise absent — it is device-scoped local state that Settings already owns.

## Subcategory favorites

```
users/{uid}/favorites/{subcategoryId}                 → { createdAt }
```

## Study sessions

```
// Written server-side by the submitStudySession Cloud Function, queued durably client-side
// (SubmitStudySessionUseCase → DefaultSessionSubmissionRepository → SessionSubmissionDeliveryWorker
// → RealSessionSubmissionApi). Shape follows studyMode: RATED carries the four counters and full
// cardResults entries below; FAST has none of the RATED-only fields at all — not zeroed, genuinely absent.
users/{uid}/sessions/{sessionId}                      → { sessionId, startTimestamp, durationSeconds,
                                                          studyMode: "rated"|"fast", isAbandoned,
                                                          categoryId, categoryName,
                                                          subcategoryIds[], subcategoryNames[],
                                                          cardCount, newCardsStudied,
                                                          // RATED only:
                                                          cardsMastered, cardsPartial,
                                                          cardsDefended, cardsDemastered }
    ... plus embedded  cardResults: { <cardId>: { subcategoryId, state,
                                                          // RATED entries only:
                                                          attemptsUsed, wasPreviouslyMastered
                                                          } }  // no transcript, ever
```

- **`sessions` is the single session collection** for both Study Modes, and **one session is one document**: aggregates, denormalized names (`categoryName`, `subcategoryNames[]`, `cardCount`) and the per-card results embedded as a `cardResults` map. Home's Recents carousel renders from one `orderBy(startTimestamp).limit(n)` query with no joins. `cardResults` is embedded rather than split into a subcollection because Firestore bills per document read — splitting saved Recents no reads while costing a write per card. The document is sealed by `studyMode`: Rated-only counters (`cardsMastered`, `cardsPartial`, `cardsDefended`, `cardsDemastered`) and Rated-only `cardResults` fields (`attemptsUsed`, `wasPreviouslyMastered`) are **absent** on a Fast document, not written as 0 — Fast has no Ratings, Attempts or mastery to report. Written by the `submitStudySession` Cloud Function; see [ADR-0049](../adr/0049-server-authoritative-session-commit.md).

## Per-subcategory progress detail

```
// progress/details is a fixed anchor doc with no fields of its own, hosting the real
// subcategories subcollection — Firestore can't nest a collection directly inside a collection.
users/{uid}/progress/details/subcategories/{subcategoryId} → { categoryId,
                                                          cards: { <cardId>: {
                                                            state: Seen|Failed|Partial|Mastered,
                                                            firstStudiedAt, masteredAt? } } }
```

- **`progress/details/subcategories/{subcategoryId}` is one packed document per Subcategory per User**, holding a `cards` map keyed by card id, and carries both progress sets: a key exists iff the Flashcard is **Studied**, and its `state == Mastered` iff it is in **Persistent Mastery**. De-mastery moves `state` down rather than removing the key, so coverage never regresses. Written by **both** Study Modes — Rated writes the Terminal State, Fast writes `Seen` only where no entry exists. **Private Flashcards never receive one.** Packing makes a session's progress cost one write per Subcategory instead of one per card, and makes any screen's progress read a single document. `details` is a fixed anchor document with no fields of its own, hosting the real `subcategories` subcollection — Firestore can't nest a collection directly inside a collection, so the packed documents sit one hop below the `progress` collection's own singletons. See [ADR-0016](../adr/0016-card-progress-model.md).
- **Partial is a Terminal State**, written to Firestore as a card's progress `state` and counted on the session record — not an in-session mechanic only.

## Per-user progress summary (ring rollup)

```
users/{uid}/progress/summary                          → { subcategories: { <subcategoryId>: {
                                                            masteredCount, studiedCount } } }
```

- **`progress/summary` is a single document per User** holding every Subcategory's `masteredCount` and `studiedCount`, so Category Details draws every ring on the screen from **one read**. The denominator is `Subcategory.cardCount` from the taxonomy, already loaded by the screens that draw rings, so it is duplicated nowhere. The denominator excludes Private Flashcards, so the card count printed beside a ring must exclude them too.

## XP, level and streak stats

```
users/{uid}/progress/user-stats                       → { xp, level, xpIntoCurrentLevel,
                                                          currentStreak, bestStreak,
                                                          lastStudyDate, goalMetDate }
```

- **`progress` holds the User's singleton documents** — the progress summary (`summary`) and the scoring state (`user-stats`) — alongside the fixed `details` anchor document that hosts the packed per-Subcategory documents one hop deeper. Firestore paths alternate collection and document, so each per-User singleton needs a fixed document id inside a collection; one security rule covers them all.

## Private flashcards

```
users/{uid}/privateCards/{subcategoryId}/flashcards/{cardId} → { question, answer, tags[], difficulty, status, createdAt }
```

- **Private Flashcards are unaffected by sharding** — `users/{uid}/privateCards/{subcategoryId}/flashcards/{cardId}` stays one document per card, since they're appended one at a time via the creation FAB rather than seeded in batch.
- Private Flashcard `status`: `"private" | "submitted" | "approved"` — promotion pipeline to global pool.

## Curation requests (report a problem)

```
// Report a problem (in-session flag icon)
users/{uid}/curationRequests/{cardId}                       → { subcategoryId: String,
                                                               actions: {
                                                                 "<CurationAction>": { flaggedAt: Timestamp }
                                                               } }
// CurationAction values: DifficultyTooEasy | DifficultyTooHard | WrongTags |
//                        NeedsCodeExample | BacktickRedo | FullRedo | Delete
```

- **`curationRequests/{cardId}` is a flat collection** keyed by globally-unique cardId. Stores structured content-fix directives raised by any user via the in-session "Report a problem" dialog, consumed by admin sync scripts — not surfaced back to users anywhere in the app. Actions are a map of `CurationAction` string → `{ flaggedAt }`. Doc is deleted when all actions are removed. See [ADR-0017](../adr/0017-curation-report-system.md).

## Offline persistence

- Offline: Firestore Android SDK built-in persistence. No Room needed.
