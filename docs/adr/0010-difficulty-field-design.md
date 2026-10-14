# Difficulty field: mandatory on every Flashcard, filtered at DTO layer for the global pool, immutable

## Decision

Every Flashcard — global admin-curated or user-created Private — carries a mandatory `difficulty: Int` (1–10). For the global admin-curated pool, Flashcard documents in Firestore that have no `difficulty` field are silently filtered out at the DTO→domain mapping layer and never reach the domain (a backfill concern, since the initial global corpus predates the field). Private Flashcards have no backfill concern — the creation dialog requires a difficulty value (Slider, 1-10, discrete steps) before a card can be saved, so no Private Flashcard can exist without one. Difficulty is immutable once written to Firestore for a global Flashcard. A User can change the Difficulty of their own Private Flashcard, and the field stays mandatory.

## Context

Curriculum features require Flashcards to be ordered by complexity within a Subcategory. A per-card integer on a 1–10 domain-relative scale was chosen over categorical tiers (beginner/intermediate/advanced) to allow fine-grained ordering.

The field is being added retroactively to the global pool — the initial card corpus predates it. During the backfill period, Firestore will contain a mix of scored and unscored global documents. No such transition period applies to Private Flashcards: none exist yet, so the mandatory field applies to them from day one, with Subcategory Details' mixed list of global + private cards able to sort/filter every card by difficulty uniformly.

## Alternatives considered

**Nullable `difficulty: Int?` in the domain model** — rejected. Null propagates into curriculum logic as an edge case every caller must handle. Filtering at the DTO boundary is cleaner: the domain never sees an unscored card, and callers make no special case for null.

**Default sentinel value (e.g. 0)** — rejected. 0 is outside the valid 1–10 range and would silently pollute curriculum ordering if not stripped everywhere. A filter is honest; a sentinel is a lie.

**Private Flashcards exempt from difficulty** — rejected. Users have no calibration anchor at self-assignment time, which argues for skipping difficulty on Private cards — but Subcategory Details' mixed list of global + private cards needs every card orderable/filterable by difficulty, and building exemption logic (private cards pinned to list-end on sort, dropped from filter results) is more work than just requiring the field. No private cards exist in Firestore yet, so there's no migration cost either way.

**Mutable difficulty (re-scoring supported)** — deferred. As anchor calibration matures, re-grading a batch of cards may become necessary. Not supported in the initial implementation; revisit when the curriculum feature is built.

## Consequences

- `FlashcardDto` maps `difficulty` as `Int?` (nullable for Firestore deserialization). The DTO→domain mapper drops any global-pool card where `difficulty` is null.
- `Flashcard` domain model carries `difficulty: Int` — non-null, always valid.
- The null-filter runs only on the global pool path (`subcategories/{subcategoryId}/shards/`, cards as map entries per ADR-0037) — a backfill concern that doesn't apply to `users/{uid}/privateCards/`, since the creation dialog's mandatory Slider means no Private Flashcard can be written without one.
- Private Flashcard creation dialog UI requires a `Slider` control (1-10, discrete steps) wired as a mandatory field, not optional.
- Seed tooling picks up `difficulty` from `inbox.jsonl` automatically — no explicit field mapping change needed.
- No `difficultyMin`/`difficultyMax` denormalization on Subcategory docs for now — deferred until a UI use case requires it.
- Difficulty anchors (`difficulty_anchors.json`) are local capture-tooling artifacts only; they never reach Firestore.
