# Consolidated component style/size tokens: `FlashcardsComponentStyle` / `FlashcardsComponentSize`

## Context

Three `core:ui` component families each modeled the same two axes independently:

- **Background context** (can a component read the surface behind it? No — so the caller
  declares it): `FlashcardsButtonStyle { Surface, OnGradient }` (ADR-0033) and the badge's inline
  `MetadataBadgeStyle { Surface, OnGradient }` — identical cases, duplicated types.
- **Size tier**: `FlashcardsButtonSize { Normal, Small }` (ADR-0033), with `56dp`/`40dp` values
  that turned out to coincide with the values [ADR-0035](0035-progress-bar-composables.md)'s new
  progress bar family also needed for its own `Normal`/`Small` tiers.

Introducing a third near-identical `FlashcardsProgressBar{Style,Size}` pair alongside these would
lock in drift permanently — a future addition to one family's cases (or a rename) would silently
stop matching its siblings.

## Decision

Both axes move to `core/ui/composables/common/`, each in its own file, and every existing
consumer is migrated onto them in this same change (not deferred):

```kotlin
enum class FlashcardsComponentStyle { OnSurface, OnGradient }
enum class FlashcardsComponentSize { Normal, Small }
```

- **`FlashcardsButtonStyle`/`FlashcardsButtonSize`** (ADR-0033) and the inline **`MetadataBadgeStyle`**
  are deleted. `FlashcardsFilledButton`/`FlashcardsTonalButton`/`FlashcardsOutlinedButton`/
  `FlashcardsTextButton`/`FlashcardsMetadataBadge` now take `FlashcardsComponentStyle`/
  `FlashcardsComponentSize` directly. `FlashcardsButtonMetrics.kt`'s `.metrics()` resolver moves
  its receiver type accordingly; its concrete `56dp`/`40dp`/padding/text-style values are
  unchanged.
- **`Surface` is renamed to `OnSurface`** on the shared type (both button and badge previously
  called it `Surface`). `OnSurface` names what the value actually means — "drawn on a plain
  themed surface, as opposed to on a gradient" — matching M3's own `onSurface`/`onSurfaceVariant`
  vocabulary instead of colliding with the unrelated `androidx.compose.material3.Surface`
  composable that most of these components are also wrapped in.
- **Each family keeps its own geometry resolver** (`FlashcardsButtonMetrics.kt`'s `.metrics()`,
  [ADR-0035](0035-progress-bar-composables.md)'s `FlashcardsProgressBarMetrics.kt`). The shared
  type only fixes the two-case *axis*; a button's `Normal` (56dp height) and a progress bar's
  `Normal` (5dp thickness) are unrelated numbers resolved independently. A family is free to add a
  case the enum doesn't have use for elsewhere — the risk flagged below, accepted deliberately.
- **Color/alpha values behind each style case are unchanged** by this migration — only the type
  they're keyed on moved. See ADR-0033's color table for buttons/badge, and ADR-0035 for progress
  bars' new `BrandColors` fields.

## Consequences

- One rename to internalize: `FlashcardsButtonStyle.Surface` / `MetadataBadgeStyle.Surface` are
  now `FlashcardsComponentStyle.OnSurface` everywhere, including at every existing button/badge
  call site.
- **Divergence risk, accepted**: if a future variant needs a size or style case one family has no
  use for (e.g. a button-only `Large` tier), the shared enum either grows a case some families
  must never construct, or the two axes split apart again at that point. Given `Style` is a stable
  2-value axis unlikely to grow, and `Size` splitting later is a straightforward, non-breaking
  change (callers matching exhaustively on `FlashcardsComponentSize` inside `core/ui` are the only
  breakage surface, and there are few), this is judged cheaper than preemptively keeping three
  near-duplicate enums in sync by hand.
- **Follow-up, not done here**: the broader `BrandColors`/`Color.kt` palette still hand-locks a
  fair number of individual component colors (see ADR-0035's new progress-bar fields, and
  ADR-0033's button table) rather than deriving them from a smaller, more systematic token set.
  Reducing that per-component color-locking is flagged as a future palette pass, out of scope for
  this consolidation.
- **Amended:** the dark theme's surfaces, glass, sheet, dialog and button fills are now tones of one
  neutral palette, frozen to hex
  ([ADR-0078](0078-dark-theme-is-frozen-tokens-from-one-neutral-palette.md)). The light palette's
  per-component values are unchanged.
- **Amended:** a component's style follows its immediate backdrop, not the screen's: `OnGradient`
  where it sits directly on a brand gradient, `OnSurface` where it sits on a card, sheet or dialog,
  even when that host is itself on a gradient.
