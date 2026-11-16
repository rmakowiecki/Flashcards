# The website's landing page is a snapping pager over one Morpheus graph

> Status: accepted

## Context

The website's home page was a logo, a tagline and two legal links
([ADR-0068](0068-website-is-a-compose-multiplatform-wasm-app.md)). It is now the app's landing page:
a hero, one page per feature with a framed app screenshot, and a closing page. The app's own launch
screens already set the look, the brand gradient behind a [Morpheus](0069-morpheus-is-an-external-library-built-from-a-submodule.md)
graph that morphs from step to step in onboarding, and the site should feel like the same product.

The page is drawn on one Compose canvas, so the browser's own scrolling, scroll snapping and
`scroll-behavior` do not apply; every input has to be handled in Compose.

## Decision

**Home is a vertical pager of full-window pages that snap**: the hero, one page per feature, the
closing page. There is one page per feature at every window width. Below the Material expanded
width (840dp) a feature page stacks its text above the phone instead of placing them side by side.

**One Morpheus graph sits fixed behind every page**, over the brand gradient, and morphs with the
pager's position exactly as onboarding's does: it follows slow scrolling exactly and, for a fast
scroll or a snap, never crosses from one page's shape to the next faster than a website-only
minimum of one second. `:web` depends on `com.rossomak:morpheus`, the same included build the app
uses, at the same pinned release.

**The site has its own graph family.** The app's family lives in `:core:ui`, which is Android-only, and
is tuned for a portrait phone. The site's family has one seed and two layers (a fine back mesh and a
sparser, brighter front one), with every page's spec in the same morph family so nodes stay in place,
and shapes composed across a wide desktop window. The two families share nothing and may drift.

**The page scrolls freely; snapping is an assist.** Wheel, trackpad and touch move the pages by their
own distance, and the page may rest between two pages while input continues. Once scrolling rests,
the pages settle on the nearest page, leaning toward the direction of travel. Keys and dots step page
by page.

**Input is handled per device:**
- Touch drags are the pager's own; a fling travels as far as its speed carries it, then settles.
- A trackpad's wheel events, momentum included, move the pages one to one. A classic mouse-wheel
  notch eases the pages by its distance, as a browser's smooth scrolling does. The browser does not
  report which device sent a wheel event, so a heuristic tells them apart (a trackpad reports pixels
  whose legacy `wheelDelta` is three times the delta; a mouse reports lines or a fixed notch size).
- Arrow up and down, Page Up and Down, Home and End move the pages; a column of dots shows the page
  and opens any page.

The window's wheel and key events are taken before Compose sees them, while the landing page shows;
the legal pages keep Compose's ordinary scrolling. Every settle and step is one short ease-out started
in the event that asked for it, independent of the graph, which catches up on its own.

**The logo is one shared element outside the pager**: large on the hero and the closing page, small
and pinned at the top on the feature pages, interpolated with the scroll position between them, as
the graph is.

**Every graph is ribbons and corner blooms.** Wide ribbons across the window and blooms in its
corners form continuous meshes; smaller shapes (rings, blocks, bars) read as scattered islands at
this scale. The front layer's leaving and arriving shapes sweep the whole morph together, so a slow
scroll changes the graph from its first pixels.

**Motion respects the visitor.** Text lags slightly behind its page and fades in as it arrives;
under the browser's `prefers-reduced-motion` it moves with its page and does not fade, and the first
load's entrance does not play.

**Only the screenshots and the legal pages' card follow the system theme.** Each feature has a
light and a dark screenshot, chosen by `prefers-color-scheme` and switched live; the legal pages set
their text on an opaque card in the app's own light or dark card colors the same way. The gradient,
the graph and the white text on it stay as they are.

## Alternatives considered

- **A long scrolling page with sections.** Simpler input, but a fixed graph that morphs per section
  would need the section boundaries anyway, and a canvas page gets no native scroll snapping.
- **Reuse the app's graph family.** It is in an Android-only module and shaped for a phone; moving it
  into a shared module would tie the app's tuning to a desktop layout it was never meant for.
- **Let Compose handle the wheel.** Compose scrolls the pager by the wheel's raw delta but never
  settles it on a page once scrolling rests.
- **Page by page: one notch or one swipe per page.** Scrolling then barely moves
  before snapping takes over; a landing page should scroll like a page.
- **A separate URL or anchor per feature.** Not needed for a short landing page, and every reload
  returning to the hero keeps the address set to the three pages of
  [ADR-0068](0068-website-is-a-compose-multiplatform-wasm-app.md).

## Consequences

- Morpheus triangulates a layer's new set of nodes on the frame a morph starts, which can cost one
  frame at a dense front layer; the front density is set where that stays a single frame.
- The mouse-or-trackpad heuristic can misread an unusual device: a smooth-scrolling mouse may glide
  instead of stepping, which still lands on a page.
- The site's bundle grows by Morpheus and the sixteen screenshots; the pager composes only the pages
  on and next to the screen, so a screenshot is decoded only when its page is near.
- Search engines still read little of the page beyond its title and description.
- Amends [ADR-0011](0011-multi-module-architecture.md) (`:web`'s dependencies).
