# Loaded content reveals as one block

> Status: accepted

## Context

Screens cut between a spinner and their content, each in its own way. Fast loads flashed the
spinner, reloads replaced content that came back at once, and per-item animations slid rows out of
clipped bounds.

## Decision

- `FlashcardsLoadStateContent` (`core:ui`) owns every screen's load state: one `AnimatedContent`
  whose plain content lambda maps each state to `loading()` or `content { … }`.
  `FlashcardsRevealContent` is the same reveal without a loading state.
- Loaded content fades in and slides up by `spacing.medium` as one block, never per item. Swaps are
  sequential and use `SizeTransform(clip = false)`, so nothing is clipped.
- The spinner appears after 250 ms and stays at least 400 ms. A reload keeps the shown content for
  the same 250 ms; a result with the same `contentKey` updates in place.
- The lambda classifies from its parameter only, never a captured value, and stays inline.
- Preview keeps its hero through a redraw (`hasDrawnSession`) and publishes the drawn values in one
  update. Its buttons stay enabled during a draw so a cached draw doesn't blink them; Start ignores
  taps until the draw lands.

## Consequences

- Fast, cached loads show no spinner and no flicker.
- A slow Preview redraw shows no progress, and a Start tap during it does nothing.
- The content lambda runs more than once per state, so it must stay cheap and side-effect free.
