# Typography is the stock M3 type scale over bundled Roboto

> Status: accepted

## Context

The app's type came from two downloadable Google Fonts: Roboto Serif for the display, headline and
title slots and Roboto for the body and label slots. They were fetched through Play Services at
runtime, so a device without Play Services, or one still downloading, drew the system font, and
`:core:ui` and `:app` each carried a copy of the provider's certificate array.

Screens also styled text ad hoc. About 47 call sites changed weight, size, family or letter spacing on
top of a slot: mostly a `fontWeight = FontWeight.Bold` on a title or label, plus `SemiBold`,
`ExtraBold`, a hardcoded `13.sp` monospace for code, and wider tracking on two onboarding labels.
Nothing kept the weights consistent, so the same role was bold on one screen and regular on the next.

## Decision

**One bundled family.** The variable `Roboto[wdth,wght].ttf` from google/fonts ships in
`core/ui/src/main/res/font/roboto.ttf` (about 0.5 MB) and is instanced at Normal, Medium, Bold and
ExtraBold.
Every slot uses it. The serif, the Google Fonts provider, the `ui-text-google-fonts` dependency and
both `font_certs.xml` copies are gone. `FontFamily.Default` was rejected because several OEMs replace
the system sans-serif, so it would not guarantee Roboto.

**The stock M3 scale, unchanged.** `AppTypography` is `Typography(fontFamily = robotoFontFamily)`:
stock sizes, weights, line heights and tracking for every slot.

**Emphasis is the M3 `*Emphasized` twin of a slot.** Material3 ships an emphasized version of all 15
slots (`titleMediumEmphasized` and so on), so text that needs weight uses that instead of a weight
override. Their weights are Material's: Medium for body, display, headline and `titleLarge`; Bold for
`titleMedium`, `titleSmall` and the labels. `SemiBold` is not used.

**Theme constants cover what the slots don't** (`core/ui/.../theme/Type.kt`):

- `Typography.code`: `bodySmall` in the system monospace, for code blocks and raw debug text.
- `InlineCodeSpanStyle`: monospace at `0.85.em`, a size relative to the text around it. Monospace
  glyphs are wider and taller than Roboto's, so full-size code outweighs its sentence; one relative
  size keeps the same ratio next to every slot, with no per-slot inline-code constants.
- `EmphasizedSpanStyle`: Medium weight for emphasis inside running text, matching the body
  `*Emphasized` slots.
- `Typography.displaySmallExtraBold` and `Typography.titleLargeExtraBold`: ExtraBold hero figures,
  the Session Summary's XP total and the Level card's Level number. The Medium of their `*Emphasized`
  twins reads too light for a number that is the point of the screen.

**One sanctioned exception.** `FlashcardsAvatar` sizes its fallback initials as a fraction of the
avatar's diameter, which no fixed slot can do.

**Enforced by Konsist.** `text is styled only through theme typography tokens` fails on any
`fontWeight =`, `fontSize =`, `fontFamily =`, `letterSpacing =`, `lineHeight =`, `TextStyle(` or
`typography.<slot>.copy(` in a Compose module's main sources, outside the theme package and
`FlashcardsAvatar.kt`. Previews are held to it too: a text-based rule cannot tell them apart.

## Consequences

- Text looks the same on every device, with no runtime download and no fallback flash.
- Headings are sans-serif. Emphasis on `headlineSmall`, `headlineMedium`, `displaySmall`, `titleLarge`
  and `bodyLarge` is Medium where it used to be Bold, except the two ExtraBold hero figures.
- A new text style needs a slot, an `*Emphasized` slot or a new theme constant; the Konsist rule
  rejects anything set at the call site.
- Code blocks are 12sp, down from 13sp. Inline code scales with its sentence instead of a fixed 13sp.
- Similar texts share a slot: list-row titles are plain `titleSmall`, row meta lines and all-caps
  eyebrows are `labelMedium`, and single-line radio and checkbox rows are plain `bodyLarge`, without
  a weight change on selection.
- Amends [ADR-0018](0018-agp9-convention-plugin-setup.md), whose font-certificate note no longer
  applies.
