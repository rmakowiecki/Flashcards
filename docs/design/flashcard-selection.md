# Flashcard Selection

How a Study Session's Flashcards are chosen and ordered. Selection runs on the Preview Study Session Screen, which owns it for every entry point ([ADR-0004](../adr/0004-preview-study-session-screen-owns-card-selection.md)): it turns the session's scope and settings into a concrete, ordered list of Flashcards, and the session itself only plays that list back. This doc covers the path from scope to cards; the screen around it is in [preview-study-session.md](preview-study-session.md).

## Which Subcategories are in scope

- A **single-subcategory** or **Custom** session uses exactly the Subcategories its entry point sent.
- A **Quick Session** samples its Subcategories from the Category's list: first a random count within the user's Subcategory count range, then that many Subcategories at random. When the Category has fewer Subcategories than the range asks for, all of them are used. Every feasible count is equally likely ([ADR-0040](../adr/0040-quick-session-subcategory-sampling.md)). When the entry point sent no list, Preview fetches it itself ([ADR-0056](../adr/0056-preview-resolves-quick-session-candidate-pool.md)).

The Quick sample is held for the life of the screen. Changing a filter, the Length or the Sort Order keeps the same Subcategories; only Reshuffle samples again.

## Loading the pool

The Flashcards of every Subcategory in scope are loaded in parallel, through the Flashcard cache ([flashcard-cache.md](flashcard-cache.md)), and combined into one pool. If any Subcategory fails to load, the whole selection fails and Preview shows its error with Retry. A partial pool is never used, because it would quietly narrow the session instead of reporting that something went wrong.

## Filters

Two filters narrow the pool, and a Flashcard must pass both:

- **Difficulty**: the Flashcard's Difficulty falls within the selected range, both ends included.
- **Tags**: the Flashcard carries at least one of the selected Tags. An empty selection means no tag filter, not "match nothing".

The filter rule is the same one Subcategory Details applies to its browsed list, so a filter can never mean one thing in a list and another in a session ([ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md)).

The tag filter applies only to a session with one Subcategory. Tags belong to the Flashcards of a single Subcategory, so a multi-topic session has no coherent tag set to offer and filters by Difficulty only ([ADR-0030](../adr/0030-preview-session-settings-sheet.md)). Tags carried over are cleared whenever the set of Subcategories changes, for example after a Quick resample.

The Tags a filter picker offers come from the whole pool, not from the filtered or drawn cards. Otherwise a Tag would disappear from the picker precisely because the user filtered it out, leaving no way to select it again.

Filters are always session-scoped and never saved as defaults. Subcategory Details is the only entry point that sends filters, carrying the Tags and Difficulty range active on that screen; every other entry point starts with no tag filter and the full Difficulty range. Resetting the filters on Preview restores whatever the entry point sent.

## Draw and order

1. **Shuffle** the filtered pool uniformly at random. There is no session seed and no weighting by past performance.
2. **Take** the session Length from the front. When the filtered pool is smaller than the Length, the session simply holds the whole pool, with no warning.
3. **Order** the drawn cards by the Sort Order. Default keeps the draw order. Easiest first and Hardest first sort by Difficulty and keep the draw order among cards of equal Difficulty.

Drawing before ordering is deliberate: sorting the whole pool and taking the head would hand back the same easiest, or hardest, cards every session. The Sort Order never changes which cards are drawn, only the order they are presented in.

The session reloads its cards by id from the same Subcategories and plays them in exactly the order Preview chose. Any id no longer found is dropped; if none are found, the session fails to load.

## When the draw runs again

- Confirming a Preview settings dialog redraws, except the Sort dialog, which reorders the cards already drawn so that a sort-only change never swaps which cards are in the session.
- Reshuffle, offered only for a Quick Session, samples new Subcategories and then redraws.
- Resetting the filters and Retry redraw.

Each redraw is a fresh shuffle, so the drawn cards can change even when the pool did not.

## Estimated duration

The Preview hero's estimated duration is the drawn card count times one fixed pace per card, rounded up so that a session with any cards never reads as zero minutes. The same pace is used for Rated and Fast sessions.

## Related

- [preview-study-session.md](preview-study-session.md): the screen that hosts selection, its settings rows and entry points.
- [flashcard-cache.md](flashcard-cache.md): how the pool's Flashcards are read.
- [browse.md](browse.md): Subcategory Details, whose filters and Sort Order a session can start from.
- [settings.md](settings.md): the saved Length, Sort Order and Subcategory count range.
- [planned/mastery-defense.md](planned/mastery-defense.md): the planned floor of mastered Flashcards in every draw.
