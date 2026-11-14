# Screen scroll edges

> Status: accepted

## Context

Each scrolling screen assembled its own fade, scroll and end padding. The gap after the last item
differed from screen to screen, Debug and the Browse search results had no fade, and the lazy
lists on Category Details, Subcategory Details and Open-source licenses clipped their group a
`spacing.normal` short of the bottom bar, so content never reached the edge and the group's
corners squared off mid-scroll. Home was the only screen that looked right.

## Decision

- Home is the reference. Scrolling content runs right up to the bar or toolbar beneath it under a
  `FlashcardsScrollFadeHeight` fade, and once fully scrolled its last element ends `spacing.normal`
  above that edge. The gap sits inside the scroll, never outside it.
- The top mirrors the bottom: the first element starts `spacing.normal` below the bar, inside the
  scroll, under a top fade.
- A plain scrolling screen uses `Modifier.flashcardsScreenScroll(scrollState)` (`core:ui`) instead
  of a bare `verticalScroll`. `padsTop` adds the top gap when the first element doesn't already
  carry it, as a `FlashcardsOverlineLabel` does.
- A screen's `LazyColumn` passes `flashcardsScreenListContentPadding()` as its `contentPadding` and
  pairs it with `flashcardsListScrollFade`, with the same `padsTop` rule and default. Its `flashcardsListGroupContainer(listState)` draws the
  group background only from the first item's top to the last item's bottom, so the group's
  rounded ends scroll in and out with its rows.
- The exception is Category Details and Subcategory Details at the top. Their count overline is
  pinned above the list, which starts flush under it (no `padsTop`), so the group's top edge
  squares off there while scrolled. Their bottom follows the rule.
- Home keeps its own assembly: each of its sections owns its bottom gap, so the helper would double it.

## Considered options

- Keep clipping the lazy lists like a window and only align the padding values. Rejected: the hard
  cut above the toolbar was the inconsistency itself.
- Have each lazy row paint its own slice of the group background. Rejected: it reshapes every
  group row, where bounding one container draw by the list's layout changes nothing at rest.
- A Konsist rule against a bare `verticalScroll` or `LazyColumn` in screens. Rejected: dialogs,
  sheets, forms and debug harnesses rightly scroll differently.

## Consequences

- The edges are set in one place; a new screen gets them by using the helpers.
- The lazy group container only works for a list that holds one group and nothing else.
- Forms and debug harnesses (Report a bug, the voice and blur test screens) stay outside the rule.
