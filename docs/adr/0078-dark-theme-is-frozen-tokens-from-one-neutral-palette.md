# The dark theme is frozen tokens from one neutral palette

> Status: accepted

## Context

Dark was Material's generated scheme plus placeholder brand colors. The stock tones put the card
(`surfaceContainerLowest`) below the background, so cards sank into the screen, and the white
on-gradient glass glowed against dark surfaces. Retuning it needed a runtime path only dark used:
tones and alphas mixed with `compositeOver` and read from a debug override, while light used fixed
tokens.

## Decision

**Light and dark are fixed values applied the same way.** `FlashcardsColors` bundles the
`ColorScheme`, `BrandColors`, `SemanticColors` and `RatingColors`. `lightFlashcardsColors` and
`darkFlashcardsColors` live in `:core:designsystem`, and `FlashcardsTheme` picks one by the system
setting. There is no dynamic color.

**Every dark surface is an HCT tone of one neutral palette** (hue 290, chroma 10), frozen to hex in
`Color.kt`, each constant naming its tone:

| Slot (role) | Tone |
|---|---|
| background, surface, surfaceDim | 6 |
| card (`surfaceContainerLowest`) | 10 |
| `surfaceContainerLow` (bottom sheet) | 11 |
| `surfaceContainer` | 12 |
| `surfaceContainerHigh` (dialog) | 13 |
| `surfaceContainerHighest` (tonal button) | 22 |
| `surfaceBright` | 24 |
| `surfaceVariant`, `outlineVariant` | 30 |
| `secondaryContainer` (list group backdrop, selected row) | secondaryDark at 10% over the card |
| filled button on a gradient (with on-surface text) | 17 |
| on-gradient glass and its progress track | 12 at 30% |

The card sits above the background, so cards lift off the screen. A role uses the same slot in both themes, and light defines the same value where it should stay flat (white sheet and dialog). `BrandColors` keeps only the gradients and what exists on them.

**Glass only on the gradient.** There, dark components use the tone-12 glass with a 12% white edge.
Sheets, dialogs, cards and filled buttons are opaque.

**Status containers are opaque mixes**: the content hue at 14% over the card tone, frozen to hex, so
a banner reads the same on a card, sheet or dialog. `RatingColors` keeps its own hex containers and,
in dark only, a 40% ring, so dark status and rating containers deliberately differ.

**The tuner is a debug-only, in-memory override.** `DebugThemeOverrides` wraps `MainActivity`, the
screen showcase and the card animation harness. While the system is dark it re-provides the theme
from the tuner's knobs; release does nothing. The knob defaults reproduce `darkFlashcardsColors`,
which a `:debug-tools` JVM test checks, and "Copy values" prints `Color.kt` constants. materialkolor
is a `:debug-tools` dependency only.

## Considered options

- **Keep deriving dark at runtime**: rejected. It means two code paths, and the HCT library would
  ship in the app and the web build.
- **Persist the tuner's values**: rejected. A restart showing the shipped theme is how a tuned value
  gets judged against it.

## Consequences

- Retuning dark: move the knobs on a device, paste the printed constants into `Color.kt`, and update
  the knob defaults until the test passes.
- The website uses the same frozen dark tokens.
- Translucent tints inside individual components are outside this freeze.
