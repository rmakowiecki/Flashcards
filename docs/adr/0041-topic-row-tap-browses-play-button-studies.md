# A topic row's tap browses; its play button studies

## Decision

Wherever a Subcategory is listed as a row — Category Details today, Browse Search results
tomorrow — the row carries **two independent tap targets with two different destinations**:

- **Tapping the row** (anywhere except the play button) opens **Subcategory Details**. It starts
  nothing.
- **Tapping the row's encircled play button** navigates straight to the **Preview Study Session
  Screen** for that one Subcategory, skipping Subcategory Details entirely.

The row is therefore a *browse* affordance and the play button is the *study* affordance. Neither
is a shortcut for the other, and the trailing chevron stays on the row precisely to signal that the
row itself still navigates.

This is now the canonical reading of "tap a topic" across the app, and it reverses what
`CONTEXT.md` said until this decision: its Study Creation section previously listed the
single-subcategory entry point as "tap a Subcategory on Category Details → Preview Study Session
Screen", i.e. the row itself started a session and Subcategory Details had no row-level entry point
at all.

Row anatomy that follows from it, in the default (non-Selection-Mode) list:

```
[ progress ring ]  Title            [ play button ] [ chevron ]
                   N cards
```

Leading is a `FlashcardsProgressRing`; trailing is a `Row` of `FlashcardsPlayButton` and
`FlashcardsChevron`. This corrects `FlashcardsPlayButton`'s own KDoc, which described itself as
"the leading element of topic and search-result rows" — it is trailing, next to the chevron, so the
two navigating affordances sit together on the edge the user's thumb reaches and the ring keeps the
leading slot it needs for a fixed-width glyph.

In **Selection Mode** the play button is not rendered at all. The trailing pair collapses to a
single checkbox and the row's whole surface becomes the toggle, so there is exactly one thing a tap
can do in that mode.

## Context

Category Details is the screen where a Category's Subcategories are listed, and it is the entry
point for three of the four Study Creation paths (single-subcategory, Quick, Custom). Until this
decision the row had one destination and the screen could not express both intents: the design
called for a play button on every row, but if the row's own tap also started a session then the
play button was decoration, and Subcategory Details — a screen with a filter sheet, a sort menu and
a full browsable card list (ADR-0022, ADR-0038) — was reachable only from Browse Search.

The forcing question was which of the two intents is the *default* one, because that is what an
undifferentiated tap on a 56dp row gets. Two observations settled it:

1. **Browsing is the recoverable action; starting a session is not.** Landing on Subcategory
   Details by mistake costs one back press. Landing in a session preview by mistake costs a back
   press *plus* the user has to notice that the sort order and filters they never chose are about
   to define a session. The lower-cost mistake belongs on the larger, less precise target.
2. **Study Creation already routes every path through the Preview screen**, so the play button is
   not a "skip the confirmation" shortcut — it skips *browsing*, not confirming. That makes the
   two destinations genuinely different flows rather than a long and short version of one flow,
   which is what would have made a single tap target defensible.

Selection Mode's long-press entry point is what makes the split affordable. A long-press on the row
is unambiguous — it cannot be confused with either tap — so adding a third intent to the row costs
no visual space and does not compete with the other two.

## Alternatives considered

**Row tap starts a session; chevron opens Subcategory Details.** The inverse split, and the one
several content apps use. Rejected because a chevron is a ~24dp target for the action with the
richer destination, and because it makes the play button redundant — two affordances (row surface
and play button) would then do the same thing, which is exactly the ambiguity this decision exists
to remove. It also puts the unrecoverable action on the imprecise target, inverting observation 1.

**Row tap opens Subcategory Details; no play button at all.** The simplest option, and it keeps the
row honest. Rejected because it makes every single-subcategory session cost an extra screen and an
extra tap, on the most common Study Creation path — and Subcategory Details' own "Start session"
CTA already does that job for users who wanted to browse first. The play button's whole value is
that it is the *only* way to start a single-subcategory session without loading a card list.

**Play button opens Subcategory Details with the session already staged.** Considered because it
would preserve a single destination for the whole row. Rejected as a worse version of both: it
still costs the extra screen, and "staged" is not a state Subcategory Details models — its CTA
reads the filters and sort the user is currently looking at (ADR-0038), so there is nothing for an
upstream tap to pre-stage.

**Row tap starts a session, long-press opens Subcategory Details.** Rejected because long-press is
already spent on Selection Mode, and because a hidden gesture is the wrong home for a primary
destination.

## Consequences

- `CategoryDetailsScreen` takes **two** Preview-navigation callbacks, not one: the CTA's
  category-scoped one (`navigateToPreviewStudySessionForCategory`, `isQuickSession = true`) and a
  new per-row one wired to the existing `NavHostController.navigateToPreviewStudySession` helper,
  which was previously reachable only from Browse Search results.
- Row taps and the play button stay **inline `onClick` lambdas** in the composable — each already
  holds the `Subcategory` it needs. Only the two bottom-toolbar CTAs go through the ViewModel's
  events `Channel`, because only they aggregate state across rows.
- Any future screen that lists Subcategories as rows inherits this contract. Browse Search results
  already render a play button; they now owe the same two destinations.
- The chevron becomes load-bearing rather than decorative. Removing it from a topic row would make
  the row look like a non-navigating container holding a single button, so it stays even where
  space is tight.
- Per-subcategory progress has no data source yet — there is no progress-summary read anywhere in
  the code, only the `CONTEXT.md` definition. The ring is fed a **deliberately fake**,
  `id`-derived value so it is stable across recomposition and scroll, documented as fake at its
  source in the same way `SubcategoryDetailsViewModel.onFavoriteToggle` is. Real mastery
  aggregation is separate work and does not change this decision, only the number the ring shows.

## Amendments

**2026-10-03 — Recents rows are an exception.** A row of Home's Recently studied list is a past Study
Session, not a Subcategory, so it has no browse target. Opening Preview is not a session start either:
the User still confirms there. The whole row therefore opens Preview to replay the session, and it has
no play button. See [ADR-0057](0057-recents-state-projection.md).
