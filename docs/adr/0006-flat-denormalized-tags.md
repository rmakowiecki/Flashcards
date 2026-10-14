# Flat denormalized tags — no tags/ collection

## Decision

Tags are stored as plain strings in a `tags[]` array on each Flashcard document. There is no `tags/` collection, no global tag registry, and no tag kind distinction in Firestore — all tags are treated equally. There is no private tag: a user-generated flashcard is Private because of where it is stored, not because of a tag it carries (see the Private flag in `CONTEXT.md`).

## Context

Tags are user-facing filter chips on Subcategory Details. The chip set for a given Subcategory is derived from the tags actually present on its loaded Flashcards. The question was whether to back these with a Firestore registry or treat them as bare strings.

## Alternatives considered

**`tags/{tagId}` collection with `subcategoryIds[]`** — rejected. Adds a collection that must be kept in sync with every card write. Tag filter chips on Subcategory Details are derived client-side from `distinct(card.tags)` over the loaded cards — a registry adds no query benefit for that use case. Tag rename (updating a name across all cards) is an admin-only operation rare enough to handle via a migration script, not a live Firestore feature.

**Per-subcategory tag subcollections** — rejected. Over-engineers a feature that is entirely read-driven client-side. Adds write complexity to every card import and Private Flashcard creation.

## Consequences

- No global tag rename without a corpus migration script.
- New tags appear automatically as cards are imported — zero admin overhead.
- Filter chips on Subcategory Details are derived from loaded cards (`distinct(card.tags)`) — no extra Firestore read.
- User-generated flashcards carry no private tag. Their Private status is derived from their storage path.
