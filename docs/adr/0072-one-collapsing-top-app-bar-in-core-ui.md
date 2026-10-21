# One collapsing top app bar in `:core:ui`

> Status: accepted

## Context

Collapsing top app bars had drifted apart. Category and Subcategory Details wrapped M3's
`LargeFlexibleTopAppBar` in a gradient, Settings and Open-source licenses used `MediumFlexibleTopAppBar`,
the Debug hub used `TwoRowsTopAppBar`, and Account had a hand-built header.

The stock flexible bars cross-fade two title copies instead of moving one, repeat the subtitle in both
rows, have no leading element that travels with the title, and cannot be dragged. The hand-built Account
header stacked a block of at least 152dp under the 64dp pinned row, so it was at least 216dp tall where
M3 means 152dp in total. Fitting the Account header into 152dp shrank the Large avatar from 88dp to 72dp.

## Decision

**`:core:ui` owns one collapsing top app bar on a slot measure policy.** `FlashcardsCollapsingTopAppBar`
has slots for the navigation icon, actions, a title leading element, title, subtitle, details and a
backdrop. `FlashcardsTopAppBar` wraps it for a plain text title and subtitle with the back arrow. The
title and leading element are single nodes placed from the collapse fraction, so they move into the
pinned row. The arithmetic is a pure geometry function, unit-tested on the JVM.

**Sizes are total heights on M3's classic tokens.** `Medium` is 112dp and `Large` 152dp expanded,
including the 64dp pinned row both collapse to, excluding the status bar. The block grows past its token
rather than clip, for a two-line title, a subtitle on a medium bar or a large font scale. It is measured
at the expanded text styles, so it never changes with the fraction it sets the travel for.

**The style paints the container; the backdrop is a layer above it.** `FlashcardsComponentStyle` picks
`surfaceContainerLowest` (`OnSurface`) or the top bar gradient (`OnGradient`), behind the status bar. The
backdrop, such as a Morpheus graph, is laid out at the expanded height and clipped as the bar collapses,
never squashed. A graph stays in the collapsed row or fades out, and on a plain surface takes the theme's
primary color at low alpha, since brand graphs are white.

**Every bar can be dragged**, even when the content cannot scroll. A drag moves the same state as the
content's scroll, and a release settles on the nearer end.

**Content changes keep the collapse position.** A new block height writes a new travel limit: a fully
collapsed bar stays collapsed, a partly collapsed one keeps its offset clamped into the new range.

A Konsist rule bans stock M3 app bars outside `:core:ui`, by import, wildcard import or fully qualified
name. The migration covers the collapsing screens only: Preview Study Session, onboarding's Skip bar and
the session headers stay on `FlashcardsGradientTopBar`.

## Considered options

- **Keep M3's flexible bars.** Rejected for the four gaps in Context.
- **A custom header per screen where the stock bars fall short.** Rejected: the Account header drifted
  to at least 216dp, and its geometry, drag and fades could not be reused.
