# Subcategory details: merged filter sheet, bottom toolbar, sort menu

## Decision

SubcategoryDetailsScreen's top app bar is reduced to: back navigation + bookmark action + overflow menu. The overflow menu is rendered even though it currently has no items, as a deliberate placeholder for a near-future item (creating a dynamic launcher shortcut for the subcategory, see `docs/design/launcher-shortcuts.md`) — this is an intentional exception to the general rule that an overflow affordance shouldn't open onto an empty menu.

Filtering and sorting move out of the top bar and into a bottom toolbar matching the visual layout already established in the Category details design (rounded floating toolbar with a docked/adjacent FAB region; the exact composable — stock M3 `BottomAppBar` vs an M3 Expressive floating toolbar — is an implementation detail to be decided when built). Left-to-right on the toolbar: Filter icon, Sort icon, Add(+) icon, then the FAB (extended pill, "Start session", primary action) on the trailing end.

Filter is a **single** entry point (not one icon per facet): tapping it opens one sheet containing both the existing Tags multi-select chips and a new Difficulty `RangeSlider` (1-10). The two facets combine with AND (a card must match the tag selection, if any, AND fall inside the difficulty range, if set); multiple selected tags still combine with OR among themselves, unchanged from the existing behavior. Sort is a separate icon opening a menu with three mutually-exclusive options: Default (original list order), Easiest first, Hardest first — not a tap-to-cycle icon, so the current state is always legible from an explicit menu rather than inferred from icon glyph alone.

Both the Filter and Sort icons show a small badge dot whenever their state is non-default, consistent with the existing filter-active indicator already used elsewhere in the app.

The existing "N OF M CARDS FILTERED" banner reflects filter count only; it does not describe sort state, since sort never changes how many cards are shown, only their order. Sort's own state is surfaced solely via its badge dot.

Filtering to zero results is a reachable state once tag and difficulty filters can combine (e.g., a rare tag with a narrow difficulty range). This requires a dedicated empty-filtered-results state (distinct from the existing loading/error states), with messaging plus a "clear filters" action, and the Start-session FAB must disable when the filtered set is empty. This state is **designed but not yet implemented** — `SubcategoryDetailsScreenState` still only models `isLoading` / `error` / `flashcards` as of this writing.

## Context

The Subcategory details screen (`SubcategoryDetailsScreen.kt`) was a placeholder: plain button, no filtering, no sorting, no bottom toolbar. This ADR is the design record for reworking it to match the target Figma ("Subcategory details + Create card") alongside the Category details bottom toolbar pattern, adding tag filtering (UI already speced), difficulty sorting, and difficulty filtering.

Difficulty-aware sorting/filtering relies on every card — global or Private — carrying a difficulty value ([ADR-0010](0010-difficulty-field-design.md)), since this screen's list mixes both.

## Alternatives considered

**Two separate filter icons (Tags, Difficulty)** — rejected. Pushes left-side icon count to 4 (plus FAB), crowds a phone-width toolbar, and reads as two things named "filter" without a grouping label.

**Sort as a tap-to-cycle icon** — rejected. Hides current state behind icon-glyph interpretation; an explicit menu keeps state legible per M3 guidance.

**Fold sort state into the "N OF M CARDS FILTERED" banner** — rejected. Conflates two independent axes (count vs order) into one line; sort's badge dot is sufficient.

**Hide the overflow menu until it has an item** — rejected for this specific case, despite being the generally-correct M3 default; kept visible now because a concrete near-future item (dynamic launcher shortcut) is already planned.

## Consequences

- `SubcategoryDetailsScreenState` needs new fields for active tag selection, active difficulty range, active sort mode, and (pending implementation) a distinct empty-filtered-results case.
- Empty-filtered-results state and FAB-disable-on-zero-results are specified here but left as a follow-up implementation task.

## Amendments

**2026-08-29 — the Sort icon carries no badge.** This ADR specified a badge dot on both the Filter
and Sort icons "whenever their state is non-default". [ADR-0038](0038-one-sort-order-and-flashcard-selection-seam.md)
makes sort order a single notion seeded from the user's saved `StudySessionPreference.SortOrder`,
which makes "non-default" ambiguous: read literally, any user whose saved preference is not
`Default` gets a permanently lit dot they cannot clear without changing their preference. The Sort
badge is dropped. **The Filter badge is unaffected** — it marks a genuinely session-scoped
selection over a well-defined empty state, and it stays.

**2026-08-29 — the empty-filtered-results state is now specified concretely.** This ADR left it
"designed but not yet implemented". `SubcategoryDetailsScreenState` now carries a sealed content
field with four cases — `Loading`, `Error`, `FlashcardsList`, `NoMatches`. There is deliberately no
fifth case for a Subcategory with no flashcards at all: `CONTEXT.md` records that a Subcategory
always contains at least one Flashcard, which also makes the repository's cache-first read path
sound (see ADR-0038). The "Clear filters" action clears tags and difficulty only and leaves sort
untouched, matching this ADR's separation of the two axes.

**2026-10-13 — what shipped differs from the Decision above in four places.**

- **Top app bar.** It carries the bookmark and an "Add to home screen" action directly. They move into the overflow menu only when the bar runs out of room. There is no empty placeholder overflow.
- **No Add(+) on the toolbar.** It is left out until Private Flashcards are built ([planned/private-flashcards.md](../design/planned/private-flashcards.md)), so the toolbar is Filter, Sort, then the Start session button.
- **Sort is a dialog, not a menu.** It has the same three options plus a "keep as default" checkbox ([ADR-0038](0038-one-sort-order-and-flashcard-selection-seam.md)). Filter is a dialog too, and both apply only on Confirm.
- **The filtered-count overline** reads "filtered to N of M", not "N OF M CARDS FILTERED".

The current behavior is in [browse.md](../design/browse.md).
