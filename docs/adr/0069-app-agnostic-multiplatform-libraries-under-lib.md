# App-agnostic multiplatform libraries live under `lib/`

> Status: accepted

## Context

The animated network-graph background began as a prototype inside `:debug-tools`. Other modules
will draw it soon, and it is meant to become a library of its own, usable outside Android and outside
this app through Compose Multiplatform. A `:core:*` module would not fit: those modules are Flashcards
internals, free to use the app's theme, domain and logging.

## Decision

### A `lib/` group of Kotlin Multiplatform modules

`:lib:networkgraph` is the first module in a new `lib/` group, in the package `com.rossomak.networkgraph`
rather than under `com.rossomak.flashcards`, so lifting it into its own repository needs no rename. Lib
modules apply the `kmp-library` convention plugin:

- targets: Android and desktop JVM;
- strict explicit API mode;
- JetBrains Compose Multiplatform artifacts through the version catalog, with no `org.jetbrains.compose`
  Gradle plugin;
- the usual Spotless, detekt and Lint.

The one exception is `:lib:networkgraph-playground`, a desktop app that hosts the tuner: it is the only
module that applies the `org.jetbrains.compose` plugin, which it needs to package a desktop application.
Nothing depends on it.

Diagnostics and tooling hooks sit behind the `@ExperimentalNetworkGraphApi` opt-in, so app modules don't
come to depend on them by accident.

### Nothing under `lib/` knows about Flashcards

A lib module never imports `com.rossomak.flashcards..`: no brand colors, no `MaterialTheme` tokens, no
`AppLog`. Konsist enforces this. A consuming module applies the branding at its call site;
`:debug-tools`'s `flashcardsPlaygroundStyle` maps `FlashcardsComponentStyle` to an inherited fill. The
lib's presets set no fills for the same reason, and the branded variants stay in the consumer.

### Color comes from an inherited, lerpable fill

A layer paints with its own `NetworkGraphLayerSpec.fill`, else its spec's `NetworkGraphSpec.fill`, else
the fill passed to `networkGraphBackground`, which defaults to the `LocalNetworkGraphFill` composition
local. One fill set once colors every graph, and a single spec or layer can still override it.

`NetworkGraphFill` is `Solid`, `Linear` or `Radial`, all plain values with geometry in fractions of the
surface, or `Custom`, a `Brush` asked once per layer and frame and once per end of a running morph.

- **Plain values** interpolate during a morph: colors in Oklab, gradient stops matched by position,
  angle, center and radius eased between the ends. A solid end acts as a one-color gradient, and a
  linear end morphing into a radial one passes through a single color half way.
- **`Custom`** can be any brush, animated shaders included. The layer is drawn in white into its
  offscreen layer, then recolored with `BlendMode.SrcIn`, so each element keeps its own alpha. A morph
  involving a custom fill draws the far end over the near one with `BlendMode.SrcAtop` at the morph
  fraction, a crossfade that works for any brush, with both ends still animating.

## Considered options

- **A `Color`-only palette.** Rejected: callers need gradient-fed graphs on plain backgrounds.
- **Only a `Brush`-returning palette**, crossfading every morph. Rejected: a crossfade between two
  gradients reads as two layers, not one gradient moving, and leaves no way to set a color once for
  every graph while overriding it for one.
- **Material3 in the engine.** Rejected: the engine depends only on Compose runtime, UI and animation.
  The tuning UI, which does use Material3, goes in a separate lib module that consumers opt into.
