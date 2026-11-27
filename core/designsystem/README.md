# `:core:designsystem` — Design tokens

The Flashcards design tokens as a Kotlin Multiplatform library, built for Android and for the
browser (`wasmJs`), so the app and the website draw from one definition of the brand.

```text
src/commonMain/kotlin/…/theme/   Color, ColorSchemes, BrandColors, SemanticColors, RatingColors, FlashcardsColors,
                                 FlashcardsMaterialTheme, Type, Spacing, Sizes, CornerRadius, Alpha, FlashcardsMotion
src/commonMain/composeResources/font/roboto.ttf   The bundled variable Roboto behind every type slot
```

- **Tokens only.** Reusable components live in [`:core:ui`](../ui/README.md), which re-exports this
  module, so feature modules never depend on it directly.
- **No platform code.** Everything is in `commonMain`. There is no dynamic color: light and dark are
  the fixed `lightFlashcardsColors` and `darkFlashcardsColors` ([ADR-0078](../../docs/adr/0078-dark-theme-is-frozen-tokens-from-one-neutral-palette.md)).
- **One way to apply them.** `FlashcardsMaterialTheme(colors)` provides the Material scheme, the
  brand, semantic and rating palettes and the type scale. The app's `FlashcardsTheme` (system
  light/dark, plus the debug tuner's override) and the website's `WebTheme` (always dark) both wrap
  it, so a new palette is provided in one place.
- **Only the pairs are public.** The per-theme palettes behind `FlashcardsColors` and the raw
  `Color.kt` values are `internal`; read a color through `MaterialTheme.colorScheme`,
  `.brandColors`, `.semanticColors` or `.ratingColors`.
- **Fonts are Compose resources**, so `appTypography()` is a composable: a resource font resolves
  inside composition on every platform.

Why it exists: [ADR-0068](../../docs/adr/0068-website-is-a-compose-multiplatform-wasm-app.md).
