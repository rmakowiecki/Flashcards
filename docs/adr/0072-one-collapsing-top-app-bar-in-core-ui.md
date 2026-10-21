# One collapsing top app bar in `:core:ui`

> Status: accepted

## Context

Collapsing top app bars had drifted apart. Category Details and Subcategory Details wrapped M3's
`LargeFlexibleTopAppBar` in a gradient. Settings and the Open-source licenses screen used a stock
`MediumFlexibleTopAppBar`. The Debug hub used `TwoRowsTopAppBar`. The Account screen had a header
built by hand, because no stock bar lets one avatar and one name travel from the expanded block into
the pinned row.

The stock flexible bars could not give the app one look:

- the title cross-fades between two copies, one per row, instead of moving;
- the subtitle repeats in both rows;
- there is no leading element that travels with the title;
- the bar cannot be dragged, only collapsed by the content's scroll.

The hand-built Account header had its own problem. It stacked a block of at least
`LargeAppBarExpandedHeight` (152dp) under a 64dp pinned row, so it was at least 216dp tall, where M3
means 152dp as the bar's total height.

## Decision

**`:core:ui` owns one collapsing top app bar, built on a slot measure policy.**
`FlashcardsCollapsingTopAppBar` takes slots for the navigation icon, actions, a title leading
element, the title, a subtitle, details under the identity row, and a backdrop.
`FlashcardsTopAppBar` is the same bar for a plain text title and subtitle with the standard back
arrow. The title and the leading element are one node each, placed from the collapse fraction, so
they move into the pinned row instead of cross-fading. The arithmetic lives in a pure geometry
function, which is unit-tested on the JVM.

**Sizes are total heights on M3's classic tokens.** `Medium` is 112dp expanded and `Large` 152dp,
both including the 64dp pinned row and excluding the status bar. Both collapse to that pinned row.
The expanded block grows past its token rather than clip when the content needs more room: a
two-line title, a subtitle on a medium bar, or a large font scale. The block height is measured at
the expanded text styles, so it never changes with the fraction it sets the travel for.

**The style paints the container; the backdrop is a layer above it.** `FlashcardsComponentStyle`
picks `surfaceContainerLowest` (`OnSurface`) or the top bar gradient (`OnGradient`), painted behind
the status bar. The backdrop, for example a Morpheus graph, is laid out at the expanded height and
clipped as the bar collapses, so collapsing reveals less of it and never squashes it. A graph backdrop
either stays in the collapsed row or fades out. On a plain surface it is drawn in the theme's primary
color, because the brand graphs are white.

**Every bar can be dragged.** A drag that starts on the bar moves the same state as the content's
scroll, and a release settles on the nearer end. This holds even when the content cannot scroll.

**Content changes keep the collapse position.** When the block height changes, the bar writes the new
travel limit. A fully collapsed bar stays fully collapsed, and a partly collapsed bar keeps its offset,
clamped into the new range.

Stock M3 app bars will be banned outside `:core:ui` by a Konsist rule, once every screen uses this
bar. That rule is planned, not yet in force.

## Considered options

- **Keep M3's flexible bars.** Rejected: the title cross-fades between two copies, the subtitle
  repeats in both rows, there is no travelling leading element, and the bar cannot be dragged.
- **A custom header per screen where the stock bars fall short.** Rejected: the Account header showed
  where that leads. It drifted to at least 216dp, and its geometry, drag and fades could not be
  reused by any other screen.
