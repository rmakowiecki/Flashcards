# Unified button component: 4 types × 2 sizes

## Decision

`core:ui` gets a family of four button composables — `FlashcardsFilledButton`, `FlashcardsTonalButton`, `FlashcardsOutlinedButton`, `FlashcardsTextButton`. Each type is its own composable (not one `FlashcardsButton(type, size, ...)` entry point), living in its own file, plus a shared `FlashcardsButtonSize` enum in its own file.

**Built on real M3 buttons.** Each composable wraps the matching M3 component directly — `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton` — rather than a shared `Surface`-based layout. Doing this gets M3's own state-layer/ripple motion and disabled-alpha handling for free instead of hand-maintaining a parallel implementation of it. This was blocked at first: M3's `Button` family enforces its own minimum height (`ButtonDefaults.MinHeight` = 40dp), which would have silently clipped a 32dp `Small` back up to 40dp. Resolved by making `Small` 40dp on the nose (see **Sizes** below) instead of fighting the floor. Shared geometry/content that isn't style-specific — size→metrics resolution and the icon+label row content — lives in `FlashcardsButtonMetrics.kt`, passed as each M3 composable's `content` lambda.

**Sizes.** Exactly two: `FlashcardsButtonSize.Normal` (56dp, a deliberate brand oversize — not tied to any M3 default) and `FlashcardsButtonSize.Small` (40dp, M3's own native "Small" tier — the default height of the classic `Button()` composable). The other M3 expressive tiers (Medium-56dp coincides with `Normal`, Large-96dp, XLarge-136dp) are not implemented — nothing in the target design uses them, and `Normal`'s 56dp is a brand choice that happens to land on M3's Medium value rather than an intentional tier match.

**Icon slot.** One param, not two: `icon: ImageVector? = null` plus an `iconPosition: IconPosition` enum (`Leading`/`Trailing`). No button in the design ever needs both a leading and trailing icon at once, so a single slot matches actual usage and keeps call sites simpler than a `leadingIcon`/`trailingIcon` pair.

**Geometry foundation.** Unlike the per-size-tier token objects (`ButtonSmallTokens` etc.), the handful of values `ButtonDefaults` re-exports publicly are used directly instead of being duplicated as custom tokens:

- Icon size: `ButtonDefaults.IconSize` (18dp), referenced directly at the icon call site — not an `AppSizes` field, and not split per size tier, since M3 doesn't split it either.
- Icon-label gap: M3's own internal `Row` already applies `ButtonDefaults.IconSpacing` (8dp) between icon and label; there's no exposed param to change it. An extra `spacing.xxsmall` (4dp) padding is added on the icon's label-facing side on top of that, landing on a 12dp (`spacing.small`) total gap — the bare 8dp read too dense against an 18dp icon.
- Min width: kept at M3's own default, `ButtonDefaults.MinWidth` (58dp), unoverridden on all four types.
- Button height (`buttonHeightNormal`/`buttonHeightSmall`) stays a custom `AppSizes` pair, not a `ButtonDefaults` reference — `Normal` is a brand-specific oversize with no M3 equivalent, and pinning `Small` to a literal 40dp (rather than reading `ButtonDefaults.MinHeight` at runtime) means it won't silently drift if a future M3 version changes that default.
- Content padding: overridden to `metrics.horizontalPadding` (`spacing.medium`=24dp `Normal` / `spacing.normal`=16dp `Small`) on `Button`, `FilledTonalButton`, and `OutlinedButton`. `TextButton` is the one exception — it keeps M3's own (tighter) default `contentPadding`, since a text button has no visible container edge and its default spacing already reads right.

**Shape.** Static full pill (stadium) at every size and type, passed explicitly as each M3 composable's `shape` param (`RoundedCornerShape(cornerRadius.full)`). No M3 expressive shape-morph-on-press — that feature targets round buttons (FABs/IconButtons) per Material docs, not labeled pill buttons.

**Elevation.** M3's own per-composable defaults, unforced — a reversal of this ADR's original all-flat-zero decision. `OutlinedButton`/`TextButton` default to 0dp regardless; `Button`/`FilledTonalButton` carry M3's small default shadow.

**Press feedback.** Default M3 ripple/state-layer only — the actual motivating reason for building on real M3 buttons rather than a bare `Surface`. No scale-down press animation.

**Colors** — mapped onto existing `Color.kt`/`BrandColors` roles rather than introducing new hardcoded values, so the button family stays consistent with the rest of the theme in both light and dark:

| Type | Container | Content |
|---|---|---|
| Filled (Surface style) | Transparent `Button` + `brandColors.ctaButtonGradient` painted as a background brush behind it (M3's `ButtonColors` can't take a `Brush`) | Fixed white — not `onPrimary`, since the gradient container is itself theme-fixed |
| Tonal | `colorScheme.surfaceContainerHighest` — lilac in light, neutral tone 22 in dark ([ADR-0078](0078-dark-theme-is-frozen-tokens-from-one-neutral-palette.md)) | `colorScheme.primary` |
| Outlined | transparent, `primary` border | `primary` |
| Text | transparent | `primary` |
| Disabled (all types/styles, except Filled-Surface below) | `onSurface` @ 12% alpha | `onSurface` @ 38% alpha |
| Disabled border (Tonal on-gradient, Outlined) | — | content-alpha tier (38%), not the container-alpha tier — kept consistent between the two bordered types |
| Disabled Filled-Surface (exception) | Same gradient brush, dimmed to 38% alpha instead of swapped to a flat fill — `ButtonColors.disabledContainerColor` can't express a dimmed brush, so the background modifier itself carries the alpha | `onSurface` @ 38% alpha, same as every other type |
| On-gradient surface | `brandColors.topBarGradient` (existing, theme-fixed) | — |
| On-gradient Filled | white | `BrandColors.onGradientFilled` = `primaryLight` (#7D3AC8), fixed in both themes |
| On-gradient Tonal/Outlined/Text | white @ varying alpha | `brandColors.onTopBarGradient` (existing, white, fixed) |

Disabled uses the standard M3 low-alpha convention (`onSurface` at reduced alpha) rather than a bespoke opaque color, since it's automatically correct in both themes without adding a new token. Filled-Surface is the one deliberate exception: dimming the CTA gradient's own alpha keeps the brand gradient recognizable when disabled, instead of hiding it behind the shared flat fill.

**On-gradient variant** is in scope now — a `style: FlashcardsButtonStyle = FlashcardsButtonStyle.Surface` param (`Surface`/`OnGradient`) on all four composables, not separate `*OnGradient` composables. Matches the identical axis already modeled this way on `FlashcardsMetadataBadge` (`MetadataBadgeStyle`). No current screen places these buttons on a gradient surface yet.

**Scope exclusions:** no loading state; representative preview/Showkase coverage per type (not the full type×size×icon-position×surface combinatorial matrix), though every preview — including on-gradient ones — shows both the enabled and disabled state side by side.

## Consequences

- `BrandColors` gains `onGradientFilled` (fixed, non-theme-flipping, like `onTopBarGradient`) (fixed, non-theme-flipping, like `onTopBarGradient`). The tonal button's colors are Material scheme slots, not `BrandColors` members.
- No `AppSizes` icon-size fields exist — icon size comes from `ButtonDefaults.IconSize` directly, so there's nothing to keep in sync if that M3 default ever changes.
- Exact on-gradient color/alpha values beyond what's specified here may be revisited once a concrete screen actually consumes the on-gradient style (none exist yet).
- `FlashcardsButtonSize` and `FlashcardsButtonStyle` are later generalized into shared `FlashcardsComponentSize`/`FlashcardsComponentStyle` types in `composables/common/`, reused by buttons, the metadata badge, and the progress bar family — see [ADR-0034](0034-consolidated-component-style-and-size-tokens.md) for the current type names and the rationale for sharing them across components; `Style.Surface` is named `.OnSurface` there.
