# Category and Subcategory Search

The search box on the Study tab finds Categories and Subcategories by name. Searching "compose" surfaces the Compose Subcategory and also the Android Category, because one of its children matched; searching "testing" surfaces every matching Subcategory across every Category, plus each of their parent Categories. Subcategories are found with a debounced live Firestore query and are never loaded in full, because that collection keeps growing and loading all of it to answer a search for a handful of names would be wasteful. Categories are matched locally against the list the Study tab has already loaded.

## Matching rule

Search matches **Category and Subcategory names only**, never Tags or Flashcard content.

Matching is **prefix-only, applied uniformly** to both. Firestore has no substring query, only range queries, which give prefix matching, so typing "avigation" does not find Navigation. Category matching runs locally and could do substring matching for free, but it does not: the search box has one matching rule end to end, rather than one that quietly differs by what is being matched.

Matching is case-insensitive. Firestore range comparisons are case-sensitive, so each Subcategory stores a lowercased copy of its name for the query to compare against ([firestore-schema.md](firestore-schema.md)).

A query below a minimum length counts as no query at all: the screen stays in its default state and no read is issued, because a single character would match a large and growing slice of the collection. The live query is also capped at a fixed number of results, as a read-cost limit for a query that runs as the user types.

Synonym and fuzzy matching (for example "async" finding Coroutines) are not supported.

## Result layout

Results render as **two sections under overline headers**, Topics first, then Categories.

**Topics**: one flat row per matched Subcategory. A row shows its Studied progress ring ([browse.md](browse.md#progress-rings)), the Subcategory name, and a secondary line led by the parent Category's small icon glyph in that Category's color ([category-icon-color.md](category-icon-color.md)), followed by the card and studied counts; the Category's name reaches only screen readers. A Subcategory that is a Favorite carries the same bookmark badge as in the default list. Each match is paired with its parent from the already-loaded Category list, and a match whose parent is missing from that list is dropped. The play button opens the Preview Study Session Screen for that Subcategory, using only data the match already carries; the row body and chevron open Subcategory Details.

**Categories**: the same row shape as the default Browse list (icon tile, name, chip line, Topic count, the bookmark badge for a Favorite, chevron), and the row opens Category Details. There is no play button, because a Category is not a study unit. The default state, with no query, shows the same rows under the same overline header.

**Which Categories appear.** The Categories section is the union, deduplicated and in the Categories' stored order, of:

1. the parent Categories of every matched Subcategory, resolved locally with no read, and
2. the Categories whose own name prefix-matches the query.

The first branch is what puts Android under a search for "compose": Android is not itself a prefix match, it appears because a child matched. The second is what finds Android for "and" when no child matches. Without both, one of those cases silently returns nothing.

**Order within Topics** is the order the range query returns, which is ascending by lowercased name. It needs no client-side sort and no composite index, and it suits prefix search: alphabetical order on a shared prefix is effectively shortest first, so the exact match (Compose) sorts above its longer extensions (Compose Navigation).

**Screen states.** The expanded search overlay is in exactly one of five states:

- **Prompt**: no query, or one below the minimum length. An empty-state card invites a search.
- **Loading**: a centered spinner, shown from the first keystroke past the minimum through both the debounce wait and the fetch.
- **Results**: the two sections above.
- **No matches**: both sections came back empty. An empty-state card, not an error.
- **Error**: the query itself failed to run, for example with no connectivity. A separate empty-state card in the error tone.

No matches and Error are deliberately distinct: a clean query that matched nothing is not a failure.

## Chip lines

Each Category row, in the default list and in search results, shows a short line of its Subcategory names, truncated by the available width.

- **Default state**: the Category's featured Subcategory names, in prominence order (card volume, descending). The seed pipeline recomputes them on every run ([firestore-schema.md](firestore-schema.md)).
- **Search results**: the Category's matched Subcategories come first, ordered among themselves by prominence (not by match position, and not by the alphabetical order the Topics section uses). The line is then backfilled from the featured names, skipping duplicates. One prominence signal is used everywhere a chip line is built.

For example, a search for "testing" gives Android the line "Testing, Compose, Background": Testing is the match, and Compose and Background are Android's next most prominent Subcategories. When the match already leads the featured names, the line is unchanged from the default. A Category with no names to show gets a placeholder line instead of an empty one.

## Read budget

The two collections are cached differently because they grow differently: Categories are few and stable, while Subcategories are more numerous and keep growing.

- **Categories** are read once when the Study tab loads and held for the life of the screen. Category matching, the parent glyph lookup and every chip line use that list, at no further cost. Search adds no listener and no cache of its own for them.
- **Subcategories** are never bulk-loaded. They are read only by the live query, which returns no more than the capped number of matches, usually just a handful. Each match already carries its Category's id and name, so grouping it under its parent needs no further read.
- **Progress rings** cost one read of the User's progress summary, whatever the search matched, from which the matched Subcategories' counts are picked out in memory. The summary is a live listener, so rings for results that arrive after a dropped connection fill in on their own.

Opening the Study tab therefore reads only the Categories. Subcategories are not touched until the query reaches the minimum length.

## Live search behavior

- The query fires after a short debounce following the last keystroke.
- Only the current query's result is ever shown. A slower result or failure for an earlier query that lands after the user typed on is discarded.
- A query typed before the Categories finished loading is run again once they arrive, since the first run had no Categories to match or to pair its Topics with.
- Results are cached in memory by exact query string for the life of the screen, so repeating a query costs no read. The cache is not prefix-aware: a query for "testin" after "testing" is a fresh live query, not a local filter of the earlier results. It is never persisted.

**Entering and leaving search.** A collapsed search bar sits above the Category list. Tapping it expands into a full-screen search window that hides the bottom navigation bar. One input field is shared between the collapsed and expanded bars, so the query text and cursor survive the transition. Once expanded, the field's leading icon becomes a back arrow and a clear button appears at its end. The two do different things:

- **Clear** empties the query but keeps the window open and focused, so a query can be retyped without leaving search.
- **Back arrow** clears the query and focus, hides the keyboard and collapses the window, restoring the default Category list.
- **System back and predictive back** are handled by the search window itself.

There is no voice input. Speech-to-text into the search field would need its own permission, consent and listening state, and a visible but inert microphone button would be worse than none.

## Layering

One use case in `core:domain` owns the whole pipeline: local Category matching, the live Subcategory query, the query cache and chip-line ranking. The Study tab's ViewModel only debounces the text input and calls it.

## Related

- [browse.md](browse.md): the Study tab, Category Details and the progress rings.
- [category-icon-color.md](category-icon-color.md): the Category icon and color shown on Category rows.
- [firestore-schema.md](firestore-schema.md): the lowercased name and featured Subcategory names fields.
