# Translucent on-gradient fills blur their backdrop

> Status: accepted

## Context

Every on-gradient component (a tonal button, a metadata badge, an info banner, the Level card) was a
flat translucent white over the brand gradient. That reads fine over a plain gradient, but text and
icons lose contrast as soon as the background carries more than a gradient: animated shapes, images,
or content scrolling underneath. Each component also carried its own alpha constants, so the same
"white on gradient" surface had drifted three ways, and borders were a mix of 1dp and 1.5dp with no
rule behind it.

## Decision

**A translucent component can blur its backdrop.** The blur sits under the component's own container,
which stays the translucent fill it always was. Whether a component blurs is a per-instance choice,
independent of its `FlashcardsComponentStyle`: every component that can blur takes a
`blursBackdrop: Boolean` parameter, placed right after `style`, that only decides whether the blur
modifier is applied. It never changes the container, border, content color or alpha, which stay
driven by `style`. The default is `style == OnGradient`, so a call site changes nothing, a future
`OnSurface` variant can opt in, and an `OnGradient` instance that will never sit over anything
animated can opt out and pay nothing.

**A thin wrapper seam.** Exactly one file, `core/ui/.../backdrop/BlurBackdrop.kt`, imports Haze
(`dev.chrisbanes.haze`, the `haze` and `haze-blur` modules); Konsist fails any other file that does.
It exposes three modifiers, so the implementation can be swapped without touching a component or a
screen:

- `blurBackdropScope()` on a common ancestor of a background and the components on it. It owns the
  backdrop state and exposes it to descendants through a `ModifierLocal`.
- `blurBackdropSource()` on any background. It reads the scope's state and registers the node as
  something to blur. The brand gradient is only the first source: an animated or image background is
  a source the same way. `BrandGradientBackdrop` is a convenience for the gradient case.
- `blurBackdrop(shape, enabled)` inside a component. It finds the nearest scope and draws a blur of
  the sources registered in it, clipped to the shape, behind the component's container. It draws the
  blur only: no tint, no fill.

There is no host composable and no `CompositionLocal` for the state, so a source is never tied to the
gradient. Scopes nest and a component uses the nearest. The `ModifierLocal` reaches subcomposed slots
(a `Scaffold`'s top bar and content) but not separate windows such as dialogs.

**The source is a sibling layer, not an ancestor.** Haze records everything drawn inside a source,
children included, and a component cannot blur itself: an effect under an ancestor source either
selects nothing (the default selection keeps only sources below the nearest ancestor source) or
crashes (`All` includes the ancestor). So a screen puts its background in a sibling `Box` behind the
content, both under one `blurBackdropScope()`:

```kotlin
Box(Modifier.fillMaxSize().blurBackdropScope()) {
    BrandGradientBackdrop()
    Scaffold(containerColor = Color.Transparent) { /* content */ }
}
```

**Inactive means absent.** Below API 31, with blur switched off through `LocalBackdropBlurEnabled`,
with `blursBackdrop = false`, or outside any scope, `blurBackdrop` returns the modifier unchanged: no
Haze node is attached, no source records a layer on its behalf, and the component looks exactly as if
it had never been applied. A source only records while some blurring component is attached to its
scope, so an idle screen pays nothing. The frame before the scope resolves is the same. Neither a
component outside any scope nor a scope that no source ever joins is detected; an effect then selects
nothing. There is no heavier fallback fill: with or without the blur the
container is the same.

**Blur radius** is one theme token, `sizes.backdropBlurRadius`, read through `LocalBackdropBlurRadius`;
only the debug screen overrides it. The blur carries no noise grain.

**One alpha token each**, in the theme: `onGradientContainer` (0.24, shared by surfaces and buttons),
`onGradientBorder` (0.35), and the red pair `onGradientLossContainer` and `onGradientLossBorder`. The
former separate outline alpha is gone; the outlined on-gradient button has a transparent fill, no
blur, and a fully opaque white border.

**Borders are a 1dp hairline**, except three groups that keep the heavier
`sizes.onGradientButtonBorder` (1.5dp): every `Flashcards*Button`, `FlashcardsOptionCard` and the
onboarding `FavoriteSubcategoryCard`.

**Opt-in for future variants.** A component that does not sit directly on a gradient today
(`FlashcardsAttemptIndicator`, `FlashcardsDifficultyBadge`, `FlashcardsIndicatorBadge`,
`FlashcardsRatingButtonRow`, `FlashcardsStepper`, `FlashcardsIconTile`,
`FlashcardsVoiceCaptureIndicator`, the progress-bar tracks and `FlashcardsOptionCard`) gets no blur.
A later on-gradient variant of one adopts it with a `blursBackdrop` parameter and one `blurBackdrop`
call.

## Consequences

- Legibility no longer depends on what the gradient background carries. Adding an animated or image
  background is one `blurBackdropSource()`.
- Every screen that shows on-gradient components restructures its background into a sibling layer,
  with no change to pixels.
- On-gradient fills are visibly lighter than before (0.18 → 0.24), and the Level card, XP rows and
  avatar borders went from 1.5dp to a hairline.
- Backdrop blur costs a render pass per component. The count is small, but a screen that puts many
  blurring components in a long list would need measuring. `blursBackdrop = false` is the per-instance
  way to skip it.
- Components inside dialogs or popups, and devices below API 31, show the plain translucent
  container with no blur.
- Swapping Haze for another library or our own code changes one file.
