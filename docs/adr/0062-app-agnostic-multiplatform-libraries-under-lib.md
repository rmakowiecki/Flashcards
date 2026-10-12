# App-agnostic multiplatform libraries live under `lib/`

> Status: accepted

## Context

The animated network-graph background began as a prototype inside `:feature:debug`. Other modules
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

Diagnostics and tooling hooks sit behind the `@ExperimentalNetworkGraphApi` opt-in, so app modules don't
come to depend on them by accident.

### Nothing under `lib/` knows about Flashcards

A lib module never imports `com.rossomak.flashcards..`: no brand colors, no `MaterialTheme` tokens, no
`AppLog`. Konsist enforces this. A consuming module applies the branding at its call site;
`:feature:debug`'s `flashcardsNetworkGraphBackground` maps `FlashcardsComponentStyle` to a palette. The
lib's presets set no colors for the same reason, and the branded variants stay in the consumer.

### Color comes from a `Brush`-returning palette

`NetworkGraphPalette.brushFor(context)` is asked once per layer and frame, and once per end of a running
morph. It can return a gradient or animated shader, not only a color.

- **`SolidColor`**: every edge, halo and dot is drawn in that color. Two solid ends of a morph blend
  color by color.
- **Any other `Brush`**: the layer is drawn in white into its offscreen layer, then recolored with
  `BlendMode.SrcIn`, so each element keeps its own alpha. A morph between two brushes draws the far end
  over the near one with `BlendMode.SrcAtop` at the morph fraction: a crossfade that works for any
  brush, custom shaders included.

Layer specs keep plain `Color`s, so they stay serializable data that a morph can interpolate.

## Considered options

- **A `Color`-only palette.** Rejected: callers need gradient-fed graphs on plain backgrounds.
- **Interpolating gradients themselves**, for example rotating an angle or sliding stops. This needs a
  gradient model the lib owns. It is deferred, not rejected: a lerpable gradient type in the spec would
  morph for real, with the crossfade kept as the fallback for arbitrary brushes.
- **Material3 in the engine.** Rejected: the engine depends only on Compose runtime, UI and animation.
  The tuning UI, which does use Material3, goes in a separate lib module that consumers opt into.
