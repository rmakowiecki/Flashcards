# Category & Subcategory Search

**Status:** Implemented.

## Overview

The Study tab search box (search categories/topics) needs to feel "smart" — searching "compose" should surface both the `Compose` Subcategory and the `Android` Category (because one of its children matched); searching "testing" should surface every matching Subcategory across every Category, plus each of those parent Categories. All of this must run on a debounced live Firestore query, never a full local cache of `subcategories` (that collection is expected to keep growing; loading it in full to answer a "handful of subcategories" search is wasteful).

This doc covers matching rules, result layout, the two Firestore schema fields it depends on, the read-cost budget for both the default Browse state and live search, and where the logic lives in the module structure. Related: [Category icon+color](category-icon-color.md) (sibling denormalized-field design), [Persistent Card Mastery](persistent-card-mastery.md) (the progress summary that will eventually supply the progress rings shown alongside matched Subcategories — see "Progress rings" below for what ships in the meantime).

## Matching rule: prefix-only, uniform

Firestore has no substring/"contains" query — only range queries, which give **prefix matching** (`field >= "compose" AND field < "compose"`). Both worked examples in this design ("compose", "testing") happen to be literal prefixes of the Subcategory names they match, so this is not a regression for the motivating cases, but it is a real narrowing from arbitrary substring matching (e.g. typing "avigation" will not match "Navigation").

Category-name matching happens entirely client-side against an always-cached list (see below) and has no such technical constraint — it *could* do true substring matching for free. It doesn't: **prefix-only is applied uniformly** to both categories and subcategories, so the search box has one matching rule end to end rather than a rule that quietly differs depending on what's being matched.

Case-insensitivity: comparisons are done against a lowercased field (`nameLower` — see below), since Firestore range comparisons are case-sensitive.

**Query floor and ceiling.** A query shorter than **2 characters** is treated as no query at all — the screen stays in its default state and no Firestore read is issued. A single character matches a large and growing slice of the collection, which is exactly the bulk load this design exists to avoid. The live query is also capped at **`.limit(20)`** — a read-cost choice for a query issued as the user types, not a hard limit. It used to be constrained to 30 or fewer by the progress-ring lookup, which fed the matched ids into a Firestore `whereIn`; that lookup is now a single document read (ADR-0016), so no external ceiling applies.

Out of scope for v1: synonym/fuzzy matching (e.g. "async" → Coroutines). Additive later via a `searchTerms: List<String>` field on Subcategory if needed — doesn't conflict with this design.

## Result layout

Search results render as **two sections under overline headers**, topics first:

```
TOPICS
  ○58%  Compose                 [▶] [›]
        ⬤ in Android
  ○86%  Compose Navigation      [▶] [›]
        ⬤ in Android

CATEGORIES
  [A]   Android                    [›]
        Compose · Compose Navigation · Coroutines…
        13 topics
```

**TOPICS** — one flat row per matched Subcategory (`FlashcardsListGroupItem.Row`): a mastery ring, the topic name, a secondary line led by the parent Category's small icon glyph in that Category's color (its name reaches only screen readers), then the card and studied counts, and two distinct tap targets — a play button and a chevron. The glyph costs nothing extra: `SearchCategoriesUseCase` pairs each matched Subcategory with its parent from the cached `categories` list keyed by `categoryId`, and drops a match whose parent is missing.

**CATEGORIES** — the existing Browse category row shape (`FlashcardsListGroupItem.DetailedRow`): icon tile, name, chip line, subcategory count, chevron. **No play button** — starting a session is a Subcategory-level action, and a Category is not a study unit.

The default (no query) state is the same category row shape it is today, under its own `CATEGORIES` overline header — the header is new; the Browse screen currently renders the group with no section label.

**Row actions.** In TOPICS, the play button navigates straight to `PreviewStudySessionRoute(categoryId, categoryName, subcategoryId, subcategoryName)` — all four arguments are already present on the matched Subcategory doc, so no extra read. The row body and chevron navigate to Subcategory Details. In CATEGORIES, the row navigates to Category Details, exactly as it does in the default state.

**Which Categories appear.** The CATEGORIES section is the **union** of:

1. the parent Categories of everything in the TOPICS section (resolved from each match's `categoryId` against the cached list — no read), and
2. Categories whose own `name` prefix-matches the query.

deduplicated, in stored `order`. Branch 1 is what puts `Android` under a search for "compose" — `Android` is not itself a prefix match, it is there because a child matched. Branch 2 is what makes a search for "and" find `Android` when no child matches at all. Without both, one of those two cases silently returns nothing.

**Ordering within TOPICS** is whatever the Firestore range query returns — `nameLower` ascending, since Firestore forces the range field to be the first `orderBy`. That needs no client-side sort and no composite index, and it is the right ranking for prefix search besides: alphabetical order on a shared prefix is effectively shortest-first, so the exact match (`Compose`) sorts above its longer extensions (`Compose Navigation`). This is a separate question from the ordering *inside a chip line*, which is prominence-based — see below.

**Screen states.** The expanded search overlay is one of five mutually exclusive states (`SearchStatus`): **Prompt** (no query, or one shorter than the 2-character floor) — a `FlashcardsEmptyState` card inviting a search, distinct from the plain "no query yet" framing this doc used to describe; **Loading** — a centered spinner, shown from the first keystroke past the floor through both the debounce wait and the live fetch, not just the fetch itself; **Results** — the two sections below; **No matches** — both sections came back empty, a `FlashcardsEmptyState` card, not an error; **Error** — the query itself failed to run (e.g. no connectivity), a separate `FlashcardsEmptyState` card in the Error tone. No-matches and Error are deliberately distinct states rather than one "nothing to show" catch-all: a clean query that matched nothing is not a failure.

## Firestore schema additions (ADR-0007)

```
categories/{categoryId}     → { ..., featuredSubcategoryNames: [String] }   (up to 5, see below)
subcategories/{subcategoryId} → { ..., nameLower: String }
```

`featuredSubcategoryNames`: the display names of the top-5 Subcategories per Category, ranked by the same card-volume-descending order that already produces each Subcategory's `order` field (`build_fixture.py`). Computed once at fixture-build time. **Not sticky** — unlike `iconSvg`/`color`, this is pure derived data with no manual curation, so every reseed overwrites it unconditionally as ranks shift with card counts.

`nameLower`: `name.lower()`, computed at fixture-build time for every Subcategory. Exists solely to make prefix-range queries case-insensitive. It is a Firestore query implementation detail with no UI consumer, so it lives on `SubcategoryDto` and the Firestore payload only — the domain `Subcategory` model does not carry it and the mapper drops it.

**Backfilling existing docs.** `seed_firestore.py` writes `subcategories` in `--skip-existing` mode by default: a doc is written only if its id is *absent*. Re-running the seed normally therefore leaves `nameLower` off every pre-existing Subcategory doc — and because a Firestore range filter **excludes documents that lack the queried field entirely**, search then returns nothing at all rather than stale results. The same applies to `featuredSubcategoryNames` on Categories, which silently falls back to the placeholder chip line.

Existing Subcategory and Category documents were backfilled with a one-off script, run once and not checked in. No repeatable backfill path is needed going forward: `seed_firestore.py` writes both fields on every new or `--overwrite`-reseeded document.

## Read budget

Two collections, two very different caching strategies, because of their size trajectory: `categories` is small and fixed (4 today); `subcategories` is larger and grows over time (84 today).

**`categories`** — fetched once per Browse screen load and held in the screen's state for the lifetime of that ViewModel. Category-name matching, the parent-glyph lookup, and both the default-state and search-mode chip lines all read from that in-memory list — zero further Firestore cost. Cost: **N reads**, N = category count. This is the existing `GetCategoriesUseCase` load, reused as-is; search adds no listener and no repository-level cache layer. (A process-wide cache or snapshot listener would cut the repeat cost across ViewModel recreations, but it would also change pull-to-refresh semantics on Browse, so it stays out of this design.)

**`subcategories`** — never bulk-loaded. Only touched by a live, debounced query scoped to whatever the user is currently typing:

```
subcategories
    .whereGreaterThanOrEqualTo("nameLower", query)
    .whereLessThan("nameLower", query + "")
    .limit(20)
```

Cost: at most 20 docs, and in practice exactly the number of matched Subcategory docs — "a handful," not the full collection. Each returned doc already carries `categoryId`/`categoryName` (existing denormalized fields, ADR-0007), so grouping a match into its parent Category needs no further read; the parent's chip-fill data comes from the already-cached `categories` list.

**Browse screen entry, end to end: exactly N reads** (one query on `categories`). `subcategories` is untouched until the user types a second character into search.

## Live search behavior

- **Debounce:** 500ms after the last keystroke before firing a query.
- **Query cache:** exact-string keyed (`"testing" → [matched docs]`), in-memory, scoped to the search ViewModel/use case lifetime — cleared on leaving the screen or process death. Not persisted. Not prefix-aware (a query for `"testin"` after already having fetched `"testing"` results is a fresh live query, not a local filter of the cached superset) — deliberately the simplest option that satisfies "don't repeat a query we already ran"; a prefix-refinement cache (exploiting that `"testin"` results ⊇ `"testing"` results) is a viable future optimization if read volume warrants it.

**Entering and leaving search.** The field uses Material 3's intended pairing: a collapsed `TopSearchBar` sits above the category list, and tapping it expands into an `ExpandedFullScreenSearchBar` — a dialog window that takes over the screen, hiding the bottom navigation bar while it's up. The field's leading icon flips from a search glyph to a back arrow once expanded, and a clear (✕) affordance appears trailing. The two are not redundant:

- **✕** clears the query text but keeps the dialog open and focus intact, so a long query can be retyped without losing the mode.
- **Back arrow** collapses the dialog: clears focus and hides the keyboard, then animates `SearchBarState` back to collapsed, which restores the default category list.
- **System back and predictive back** are handled by the dialog itself — M3 owns both, so no `BackHandler` is needed on the screen.

There is **no voice/mic affordance** in v1. Speech-to-text into the search field is a separate feature with its own permission, consent, and listening-state surface, and shipping a visible-but-inert mic button is worse than shipping none.

**The field is Material 3's `SearchBarDefaults.InputField`, shared between the collapsed `TopSearchBar` and the expanded dialog** — one composable lambda passed to both, so the query text and cursor position survive the transition between them. `SearchBarState` owns expansion, `TextFieldState` owns the query text; both are synced to `BrowseViewModel` state one-way (state → ViewModel), so the debounce, minimum length, cache, and matching rules are untouched by the M3 plumbing.

Taken from M3 as-is: the pill container and colors, the IME "search" action, the search/suggestions-available accessibility semantics, the two-way coupling between focus and expansion, back and predictive-back handling, and the keyboard insets on the expanded results (no `imePadding()` needed — that's the dialog's problem). M3 1.4.0 ships only the edge-to-edge full-screen expanded style; the "contained" look (a `Surface` distinct from the input field's own container, an inset field, a transparent divider) is achieved with `SearchBarColors`/`Modifier` configuration at the call site, not a supported style flag.

## Chip-line construction

Both the default Browse state and live search results render a short list of Subcategory names under each Category row (`Compose · Coroutines · Compose Navig…`, truncated by available width in the UI, not by a fixed count — the underlying data always carries up to 5 names).

**Default state:** `Category.featuredSubcategoryNames` directly, in stored order (card-volume descending).

**Search-mode (query active):** matched Subcategory(ies) for that Category come first, then the list is backfilled — skipping duplicates — from `featuredSubcategoryNames`/prominence order until 5 names are reached. If more than one Subcategory within the same Category matches the query, the matches themselves are ordered by their own prominence `order` (not match position, not alphabetically, and *not* the `nameLower` ordering the TOPICS section uses — that ordering is a property of the flat result list, not of a chip line). One prominence signal, used everywhere a chip line is built, with no special case for the multi-match branch.

Example: query "testing" → Android chip line is `Testing, Compose, Background` (Testing is the match; Compose/Background are Android's next-most-prominent Subcategories, unrelated to the query, filling out the line).

Note that for some queries the reordered line is indistinguishable from the default one — a query matching a Subcategory that already leads `featuredSubcategoryNames` reorders nothing. That is a coincidence of the data, not evidence the reordering step can be skipped.

## Progress rings on matched Subcategories

Subcategory rows in search results carry a mastery-percentage ring. The data behind it comes from the per-user progress summary in [Card Progress and Persistent Mastery](persistent-card-mastery.md) — **one document read**, whatever the search matched, from which the matched Subcategories' counts are picked out in memory. The ring's denominator is `Subcategory.cardCount`, which the matched rows already carry. Search issues no additional per-Subcategory read at all.

The ring shipped rendering a **0% placeholder** so the row's layout and leading slot were final from day one. Wiring the real value is tracked as its own ticket alongside the Category Details rings, and is a one-call-site change: the summary is a single document for the whole user, so one read serves any result set.

## Explicitly deferred (not v1)

- Voice/speech-to-text search input.
- Synonym/fuzzy matching (`searchTerms` field).
- Prefix-refinement query cache (serving a longer-prefix query by filtering a cached shorter-prefix superset locally).
- Persisting the query cache across sessions.
- A process-wide `categories` cache or snapshot listener spanning ViewModel recreations.

## Layering

A `SearchCategoriesUseCase` in `core:domain` owns the whole pipeline: local category-name matching, the debounced live Subcategory query, the exact-string query cache, and chip-fill ranking. The Study tab ViewModel owns only the debounced text-input `Flow` and calls the use case — matches the existing `GetFlashcardsUseCase` pattern rather than putting query/cache/ranking logic directly in the ViewModel.
