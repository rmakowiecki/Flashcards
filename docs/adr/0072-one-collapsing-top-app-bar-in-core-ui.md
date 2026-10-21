# One collapsing top app bar in `:core:ui`

> Status: accepted

## Context

Collapsing top app bars had drifted apart. Category and Subcategory Details wrapped M3's
`LargeFlexibleTopAppBar` in a gradient, Settings and Open-source licenses used `MediumFlexibleTopAppBar`,
the Debug hub used `TwoRowsTopAppBar`, and Account had a hand-built header.

The stock flexible bars cross-fade two title copies instead of moving one, repeat the subtitle in both
rows, have no leading element that travels with the title, and cannot be dragged. The hand-built Account
header stacked a block of at least 152dp under the 64dp pinned row, so it was at least 216dp tall where
M3 means 152dp in total.

## Decision

**`:core:ui` owns one collapsing top app bar on a slot measure policy.** `FlashcardsCollapsingTopAppBar`
takes slots; `FlashcardsTopAppBar` wraps it for a plain text title and subtitle with the back arrow. The
title and leading element are single nodes placed from the collapse fraction, so they move into the
pinned row. The arithmetic is a pure geometry function, unit-tested on the JVM.

**Sizes are total heights on M3's classic tokens.** `Medium` is 112dp and `Large` 152dp expanded,
including the 64dp pinned row both collapse to, excluding the status bar. The block grows past its token
rather than clip, for a two-line title, a subtitle on a medium bar or a large font scale. It is measured
at the expanded text styles, so it does not depend on the collapse fraction.

**The style paints the container; the backdrop is a layer above it.** `FlashcardsComponentStyle` picks
`surfaceContainerLowest` (`OnSurface`) or the top bar gradient (`OnGradient`), behind the status bar. The
backdrop, such as a Morpheus graph, is laid out at the expanded height and clipped as the bar collapses,
never squashed.

**The title wraps while expanded.** On a `Large` bar the title takes two lines until the bar is half
collapsed, then one, ellipsized; a `Medium` title is always one line.

**Every bar can be dragged**, even when the content cannot scroll, and a release settles on the nearer
end. A new block height keeps the collapse position: a fully collapsed bar stays collapsed, a partly
collapsed one keeps its offset clamped into the new range.

A planned Konsist rule will ban stock M3 app bars outside `:core:ui` once every screen uses this bar.

## Considered options

- **Keep M3's flexible bars.** Rejected for the four gaps in Context.
- **A custom header per screen where the stock bars fall short.** Rejected: Account's header already
  drifted, and its geometry, drag and fades could not be reused.
