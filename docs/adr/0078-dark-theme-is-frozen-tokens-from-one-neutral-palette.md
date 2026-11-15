# The dark theme is frozen tokens from one neutral palette

> Status: accepted

## Context

The dark theme started as Material's generated dark scheme plus placeholders in `darkBrandColors`.
The stock dark tones put the card (`surfaceContainerLowest`, the app's card color in both themes)
*below* the background, so cards sank into the screen instead of lifting off it as white cards do in
light, and the white on-gradient glass glowed against the dark surfaces around it.

To retune it on a device, the theme read its dark colors from a composition local that a debug tuner
overrode live, and derived the glass, sheets, dialogs and status containers from that tuning at
runtime with `compositeOver`. Light used fixed tokens; dark used a different, computed path, and
`FlashcardsTheme` also kept an unused dynamic-color branch.

## Decision

**Light and dark are one pair of fixed values applied the same way.** `FlashcardsColors` bundles the
Material `ColorScheme`, `BrandColors`, `SemanticColors` and `RatingColors`; `lightFlashcardsColors`
and `darkFlashcardsColors` live in `:core:designsystem`. `FlashcardsTheme` picks one by the system
setting and provides it. There is no dynamic color.

**Every dark surface is an HCT tone of one neutral palette, hue 290 and chroma 10, frozen to hex** in
`Color.kt`, each constant naming its tone:

| Slot | Tone |
|---|---|
| background, surface, surfaceDim | 6 |
| card (`surfaceContainerLowest`) | 10 |
| `surfaceContainerLow` / `surfaceContainer` / `High` / `Highest` / `surfaceBright` | 11 / 12 / 17 / 22 / 24 |
| `surfaceVariant`, `outlineVariant` (option card ring, dividers) | 30 |
| bottom sheet | 12 |
| dialog | 14 |
| tonal button | 22 |
| filled button's ink pill on a gradient, with on-surface text | 17 |
| on-gradient glass and its progress track | 12 at 30% |

The containers above the card keep Material's dark spacing from it. The card sits above the
background, so cards lift off the screen.

**Dark glass, opaque everything else.** On the brand gradient, dark components use the tone-12 glass
with a white edge at 12% instead of light's white glass. Sheets, dialogs, cards and the filled
button are opaque. Option cards stay transparent with their ring. The dark progress bar on a surface
fills with `primaryDark` over a track of the same color at 16%, as the progress rings draw theirs.

**The status containers are opaque mixes.** Each dark `SemanticColors` container is its content hue
at 14% over the card tone, frozen to hex, so a banner reads the same on a card, a sheet or a dialog.
The grouped-list container is `secondaryDark` at 5% over the card. `RatingColors` stays a separate
palette with its own hex containers and, in dark only, a ring in the content color at 40%; in light
the status containers equal the rating containers, in dark they deliberately differ.

**The tuner is a debug-only, in-memory override inside the theme.** In the debug build,
`DebugThemeOverrides` sits inside `FlashcardsTheme` in `MainActivity`, the screen showcase and the
card animation harness; while the system is dark it re-provides the theme from the tuner's knobs
(`FlashcardsTheme(colors = …)`), built from `darkFlashcardsColors` with the tuned slots replaced.
The release variant does nothing. The knobs live in memory only. Their defaults reproduce `darkFlashcardsColors` exactly, which a JVM
test in `:debug-tools` checks, and "Copy values" prints `Color.kt` constants ready to paste.
materialkolor, for the HCT math, is a `:debug-tools` dependency only.

## Considered options

- **Keep deriving dark at runtime from tones and alphas** — rejected. Two code paths for one theme,
  the HCT library in the shipped app, and the web build would need it too.
- **Dark status containers equal to the rating containers** — rejected. The rating hexes are
  authored for the rating buttons; the mixes keep every banner on the dark card's hue.
- **Persist the tuner's values across restarts** — rejected. A restart showing the shipped theme is
  what makes a tuned value safe to judge against it, and the frozen tokens are the only durable copy.

## Consequences

- Retuning dark means moving knobs on a device, copying the printed constants into `Color.kt` and
  updating the knob defaults until the test passes again.
- The website, fixed to the dark scheme, shows the same frozen dark tokens as the app.
- Translucent tints inside individual components are not part of this freeze; each is judged on its
  own.
