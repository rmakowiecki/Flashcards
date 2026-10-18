# Branded M3 design system: additive BrandColors layer over intact ColorScheme

## Decision

Keep the full Material 3 `ColorScheme` intact and unmodified. App-specific colors that have no M3 semantic equivalent are exposed through an additive `BrandColors` class, accessible via `MaterialTheme.brandColors`. Composables read M3 roles from `MaterialTheme.colorScheme.*` and branded extras from `MaterialTheme.brandColors.*`. Raw `Color.kt` tokens are never read directly in composables.

## Context

The app has a strong visual identity (purple gradients, branded category tints) that M3's semantic color roles don't cover. Options for exposing these were: extend `ColorScheme` with extra slots, replace M3 theming entirely, or add a parallel layer alongside M3.

## Alternatives considered

**Extend `ColorScheme` directly** — rejected. `ColorScheme` is a data class with a fixed set of named semantic roles. Adding custom slots requires wrapping or forking it, which breaks M3 component defaults and creates a maintenance burden on every M3 library update.

**Custom theme, no M3** — rejected. M3 components (TopAppBar, NavigationBar, BottomSheet, etc.) rely on `MaterialTheme.colorScheme` for their default colors, elevation overlays, and state layers. Removing M3 means reimplementing all of that.

**Read `Color.kt` tokens directly in composables** — rejected. Hardcodes light/dark variants at the call site, bypasses the theme entirely, and makes dark mode support ad-hoc.

## Consequences

- All M3 components continue to work unmodified — no overrides, no forked defaults.
- `BrandColors` slots are added incrementally as new screens need them; no upfront registry required.
- Dark mode support for branded colors is handled in `BrandColors` in one place, not scattered across composables.
- A composable that needs a branded color must use `MaterialTheme.brandColors.*` — the pattern is consistent and greppable.

## Amendment: per-category colors are data, not `BrandColors` slots

Per-category tint (`Category.color`, a Firestore-authored hex string) is explicitly **out of scope** for `BrandColors`. `BrandColors` is for a small, fixed, app-wide set of semantic extras known at compile time; category colors are an open, content-managed set that grows as categories are added in Firestore, with no corresponding code change. They are parsed from the hex string to `Color` at the UI edge (see `docs/design/category-icon-color.md`) and used directly, the same way any other piece of remote content (text, an image) would be — this does not violate the "no raw `Color.kt` tokens" rule, since the value never originates from `Color.kt`.

## Amendment: the compiler enforces the rule

The palette in `:core:designsystem`'s `Color.kt` is `internal`, so only the module's own `ColorScheme`, `BrandColors` and `SemanticColors` definitions can read it, and a composable in any other module cannot import a raw token at all. A theme-independent color is still a `BrandColors` slot with the same value in both themes, as the code block's `codeBlockBackground` and `onCodeBlock` are.
