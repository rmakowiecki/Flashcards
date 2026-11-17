# The website's landing page is a snapping pager over one Morpheus graph

> Status: accepted

## Context

The website's home page ([ADR-0068](0068-website-is-a-compose-multiplatform-wasm-app.md)) becomes the
app's landing page and should look like the app's launch screens: the brand gradient behind a
[Morpheus](0069-morpheus-is-an-external-library-built-from-a-submodule.md) graph that morphs from step
to step. The site is one Compose canvas, so the browser's own scrolling does not reach its content.

## Decision

- **Home is a vertical pager of full-window pages that snap**: the hero, one page per feature, and the
  closing page. A feature page sits side by side when the window is at least 0.9 times as wide as tall,
  and stacks otherwise. The phone is sized by the window's height, so the shape decides the layout, not
  the width.
- **The browser scrolls and snaps.** Only the browser knows when fingers rest on a trackpad or when a
  wheel event is momentum. The landing page's body is a scroll container with mandatory CSS scroll snap
  and `scroll-snap-stop: always`, with one invisible stop per page. The canvas stays pinned, and the
  pager follows the body's scroll position. Window wheel events are kept from Compose, except over the
  local tuner. Keys, page dots and the scroll hint call the browser's smooth scroll.
- **One backdrop serves the whole site.** The site owns the gradient and one graph, and pages crossfade
  over it. The pager drives the graph. A legal page morphs it to the closing page's calm shape. A morph
  never crosses from one shape to the next faster than one second.
- **The site has its own graph family**, separate from the app's Android-only, portrait-tuned one. It
  has one seed, two layers, and every spec in one morph family, so nodes stay in place.
- **Logos**: the large logo is part of the hero's and closing page's text block. A small logo pinned
  outside the pager slides in on the feature pages.
- **Motion respects `prefers-reduced-motion`.** Under it nothing tweens, fades or reveals.
- **A tuner pane on `morpheus-tuner`** ships in the bundle but opens only on a local host.
- `:web` depends on `com.rossomak:morpheus` at the app's pinned release.

## Consequences

- The snap's speed and easing belong to the browser. They differ slightly between browsers and cannot
  be tuned.
- Trackpad momentum that arrives after a snap may scroll one more page.
- A morph's start can cost one frame for triangulation. The front layer's density is set so this stays
  a single frame.
- The bundle grows by Morpheus and sixteen screenshots. Only pages on or next to the screen compose.
- Search engines still read little beyond the page title and description.
- Amends [ADR-0011](0011-multi-module-architecture.md) (`:web`'s dependencies).
