# Icon button: a fifth button type, amending ADR-0033

## Decision

`core:ui` gets a fifth button composable, `FlashcardsTonalIconButton`, living alongside the four
labeled types from [ADR-0033](0033-unified-button-component-types-and-sizes.md) in
`composables/buttons/`. It replaces `FlashcardsPlayButton`, a one-off circular icon button with
its colors hardcoded to `secondaryContainer` and its size hardcoded to `sizes.iconTile`, which is
now deleted. The Preview study session screen needs the same circular button rendered on the
brand gradient, which `FlashcardsPlayButton` could not express — generalizing the existing
component was preferred over growing a second near-identical one.

**Signature.** `icon: ImageVector`, a **required** `contentDescription: String` (an icon button has
no label to carry meaning, so there is nothing else for TalkBack to announce), `onClick`, plus the
family's usual `size: FlashcardsComponentSize` (`Normal` = 56dp, `Small` = 40dp — the same
`buttonHeightNormal`/`buttonHeightSmall` tokens the four labeled types already use, reused directly
since the button is a square/circle at either tier), `enabled` and
`style: FlashcardsComponentStyle` (`OnSurface`/`OnGradient`). No `variant` axis on this component:
both known callers at the time were tonal, so a `variant` spanning filled/outlined/text would have
been inventing combinations nobody had designed.

**Built on `FilledIconButton`**, for the same reason ADR-0033 built the labeled types on real M3
buttons rather than a `Surface`-based layout: the touch target, state layer and press behavior come
for free instead of being hand-maintained. Disabled-alpha colors are shared with the rest of the
family via `disabledButtonContainerColorFor`/`disabledButtonContentColorFor` (`FlashcardsButtonMetrics.kt`).

**Colors — tonal only:**

| Style | Container | Content |
|---|---|---|
| OnSurface | `colorScheme.secondaryContainer` | `colorScheme.onSecondaryContainer` |
| OnGradient | `brandColors.onGradientContainer` | `brandColors.onGradientContent` |

Same tokens `FlashcardsTonalButton`'s on-gradient branch already uses, kept consistent rather than
introducing a parallel pair for one more component.

**Renamed to `FlashcardsTonalIconButton`, plus a gradient-filled sibling.** A later need (the Fast/
Rated study sessions' voice play/pause transport control) called for an icon-only button filled
with the CTA gradient — the same visual weight as `FlashcardsFilledButton`, not this component's
tonal treatment. Rather than adding a `variant` axis here (the exact combination this ADR's
original decision refused to invent), `FlashcardsIconButton` was renamed to `FlashcardsTonalIconButton`
and a new sibling, `FlashcardsFilledIconButton`, was added alongside it — mirroring how the four
labeled types are separate composables per treatment rather than one composable with a variant
enum. The "no variant axis" reasoning above still holds for what `FlashcardsTonalIconButton`
itself covers; it just no longer implies there is only one icon-button type in the family.
`FlashcardsFilledIconButton`'s color scheme is resolved by the same `filledButtonColorsFor` helper
`FlashcardsFilledButton` uses (`FlashcardsButtonMetrics.kt`), so the two gradient-filled types can't
drift out of sync.

## Rejected alternative: nullable `text` on the four existing types

Considered making `text: String?` on `FlashcardsFilledButton`/`Tonal`/`Outlined`/`Text`, where a
`null` value yields an icon-only rendering instead of adding a fifth composable. Rejected:

- M3's `ButtonDefaults.MinWidth` (58dp), deliberately left unoverridden by ADR-0033, would make a
  56dp-tall icon-only button 58×56 — visibly non-circular.
- The family's overridden content padding (`spacing.medium`/24dp `Normal`, `spacing.normal`/16dp
  `Small`) turns an icon-only button into a lozenge, not a circle.
- `contentDescription` would need to be mandatory exactly when `text` is null and meaningless
  otherwise — a validity pairing the signature can't express. Its failure mode is a shipped button
  TalkBack announces as nothing.
- Building on `Button` instead of `FilledIconButton` re-implements icon-button touch-target/ripple
  behavior by hand — exactly what ADR-0033 refused to do for the labeled types.

## Consequences

- `FlashcardsPlayButton` is gone. Its four call sites — Category Details topic rows, Browse
  search-result rows, and `FlashcardsListGroup`'s own two Showkase items — now call
  `FlashcardsTonalIconButton(icon = Icons.Default.PlayArrow, size = FlashcardsComponentSize.Small, …)`,
  pixel-identical to the old `sizes.iconTile` (40dp = `Small`'s `buttonHeightSmall`).
- `AppSizes.iconTile` is no longer referenced by any button; it stays defined for other tile-shaped
  uses (`FlashcardsIconTile`).
- ADR-0033's "four types" framing is superseded by five; ADR-0034's shared `FlashcardsComponentSize`/
  `FlashcardsComponentStyle` types are unchanged and reused as-is.
