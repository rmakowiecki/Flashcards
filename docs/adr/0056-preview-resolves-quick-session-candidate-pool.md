# Preview resolves the Quick Session candidate pool when the caller supplies none

> Status: accepted

## Decision

### The route says who supplies the pool

A Quick Session samples its Subcategories from a candidate pool ([ADR-0040](0040-quick-session-subcategory-sampling.md)).
`PreviewStudySessionRoute` already carries the Category id on every route, but ids alone cannot say
whether the Subcategory list is a pool to sample or a literal selection, so `isQuickSession` stays.
What changes is that the Subcategory ids become optional for Quick:

| Quick | Subcategory ids | Meaning |
|---|---|---|
| true | present | The caller's candidate pool. Preview samples it and makes no fetch (Category Details) |
| true | empty | Preview fetches the Category's Subcategories itself, then samples them (Home) |
| false | present | Custom or single-Subcategory session. The ids are used literally |
| false | empty | Invalid. `PreviewStudySessionViewModel` rejects it at construction |

A caller that already holds the list keeps passing it, so there is no second network round trip.
A caller that holds only a Category id, such as the Home Favorites carousel, passes none and does
not have to load Subcategories or handle that load failing.

### Complete pool or none

Ids on a Quick route mean "the Category's complete Subcategory list", never a partial one. Preview
cannot know the full list without fetching it, so it cannot validate a subset, and a partial pool
would silently give a Quick Session over only that subset. A deliberate subset Quick Session would
be a separate feature with its own route shape.

### The pool is fetched once and held

Preview reads the pool through `GetSubcategoriesUseCase` the first time it needs one, holds it, and
never refetches. Filter, length and sort changes reuse the sample, and Re-randomize re-rolls the
sample from the held pool (ADR-0040). A failed fetch lands on the screen's existing load-error state.
Retry re-attempts the fetch, then the draw. Re-randomize with no held pool does nothing.

### The pool uses the repository's default read

`FlashcardRepository.fetchSubcategories` reads the server first and falls back to the cache when
offline, as Category Details already does. A cache-first read was rejected:

- A cache query can return a partial Subcategory set, because other reads (the Favorites lookup, Browse search) only cache what they fetched, and there is no way to tell it is partial. A partial pool breaks the complete-pool rule above.
- Subcategory reads have no freshness policy. The seed-versioned invalidation of [ADR-0039](0039-seed-versioned-flashcard-cache-invalidation.md) covers flashcards only.
- It would change the read source for every Subcategory read, not only this one.

## Consequences

- Preview owns one more failure mode on the Quick path: the pool fetch. It reuses the existing error state and Retry.
- Category Details is unchanged.
- This builds on the Preview screen owning card selection ([ADR-0004](0004-preview-study-session-screen-owns-card-selection.md)) and on the single sort and selection seam ([ADR-0038](0038-one-sort-order-and-flashcard-selection-seam.md)).
