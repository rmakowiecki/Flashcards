# `:core:designsystem` — Design tokens

The Flashcards design tokens as a Kotlin Multiplatform library, built for Android and for the
browser (`wasmJs`), so the app and the website draw from one definition of the brand.

```text
src/commonMain/kotlin/…/theme/   Color, ColorSchemes, BrandColors, SemanticColors, Type, Spacing, Sizes, CornerRadius, FlashcardsMotion
src/commonMain/composeResources/font/roboto.ttf   The bundled variable Roboto behind every type slot
```

- **Tokens only.** Reusable components live in [`:core:ui`](../ui/README.md), which re-exports this
  module, so feature modules never depend on it directly.
- **No platform code.** Everything is in `commonMain`. Android-only theming (dynamic color) stays in
  `:core:ui`'s `FlashcardsTheme`; the website wraps the same tokens in its own `WebTheme`.
- **Fonts are Compose resources**, so `appTypography()` is a composable: a resource font resolves
  inside composition on every platform.

Why it exists: [ADR-0068](../../docs/adr/0068-website-is-a-compose-multiplatform-wasm-app.md).
