# Progress bar composables: linear, segmented, circular

**Partially superseded:** the generic circular ring described below was removed; see
[Amendment: one ring, the list-row ring](#amendment-one-ring-the-list-row-ring).

## Decision

`core:ui` gets three progress composables, in `composables/progress/`, each in its own file — one
per distinct shape, not one `FlashcardsProgressBar(shape, ...)` entry point. The shapes take
genuinely different inputs — `Float` progress for linear/circular vs `segmentCount: Int` +
`filledSegmentCount: Int` for segmented — so a single entry point would need shape-conditional
nullable params, worse than three real function signatures. (The button family took the same
per-type-composable shape in ADR-0033, but that split was driven by shared-shape/different-chrome;
it's a parallel, not the reason this split exists.)

- `FlashcardsLinearProgressBar` — continuous fill, covers every continuous-progress use case
  (session progress, XP/level bars). Not split further by "Gradient card" vs "plain card" — those
  are just the two `FlashcardsComponentStyle` cases (ADR-0034), not separate components.
- `FlashcardsSegmentedProgressBar` — N discrete, equal segments, each binary filled/unfilled (no
  third "current step" visual state). Kept as its own composable rather than a
  `segmentCount: Int?` parameter on the linear bar — discrete step progress and continuous `Float`
  progress are different enough inputs that folding them into one signature would mean one or the
  other parameter is always unused.
- `FlashcardsCircularProgressRing` — determinate ring, `Normal`/`Small` via `FlashcardsComponentSize`.
  **Superseded:** removed, see the final Amendment.

**Deliberately excluded from this batch:**

- The 88dp tap-to-edit daily-goal ring. Non-standard size, and its pencil-icon edit affordance is
  a materially different interaction contract (editable control, not read-only display) — this is
  a distinct future component, not a variant of `FlashcardsCircularProgressRing`, built when the
  Progress tab work reaches it.
- Any composite card that *uses* these bars (the Level/XP card with avatar + rank badge + bar, the
  "Continue learning" card, list rows). These primitives are the only thing in scope — composite
  cards compose them later, against real domain data, not speculative slots built now.

**Dumb primitives — no text, no card chrome, with one exception.** None of the three draw a label,
a count, or a surrounding `Card`/border — `style: FlashcardsComponentStyle` only picks track/fill
*colors*, it never draws a container. Callers wrap them in their own `Text`/`Card`/`Row`. The one
exception: `FlashcardsCircularProgressRing` takes a trailing `content: @Composable () -> Unit = {}`
slot, rendered centered inside the ring via an internal `Box(contentAlignment = Alignment.Center)`.
Every real use case for a ring centers a percentage or count inside it, so omitting the slot would
just push the identical `Box` + centering logic onto every call site; the linear and segmented
bars have no equivalent recurring need, so they stay slot-free.

**Why not build on real M3 `LinearProgressIndicator`/`CircularProgressIndicator`, unlike buttons
(ADR-0033)?** Checked against M3 1.4.0 source, not just docs. Both hardcode their own size as the
*last* modifier in their chain — `LinearProgressIndicator` ends with
`.size(LinearIndicatorWidth, LinearIndicatorHeight)` where `LinearIndicatorWidth = 240.dp` is a
fixed, `internal` token; `CircularProgressIndicator` ends with `.size(CircularIndicatorDiameter)`,
also fixed. A trailing `.size()` wins over anything the caller passes in (`fillMaxWidth()`, a
custom `Modifier.size(56.dp)`), so there is no public seam to make the linear bar span a card's
full width or hit our 56dp/40dp/88dp diameters — the two things this design most needs. Buttons
hit an analogous wall (M3's `ButtonDefaults.MinHeight` floor) but it was solvable by picking
`Small = 40dp` exactly; there's no equivalent escape hatch here short of forking M3 source.

What wrapping M3 directly would *not* have actually bought us, so the decision is legible as a
real wall and not just an unexamined preference: no animation gap (M3's own KDoc says "by default
there is no animation between progress values" and recommends wrapping with `animateFloatAsState`
yourself — exactly what we do); no accessibility gap (M3 sets the identical
`progressBarRangeInfo` semantics we set by hand). The only things M3 would add are Expressive's
default stop-indicator dot and track/fill gap, which this design doesn't use — using M3 would mean
disabling those (`gapSize = 0.dp`, `drawStopIndicator = {}`), not gaining them, the same kind of
opt-out buttons already made against M3's shape-morph.

**Sizes.** `FlashcardsComponentSize.Normal`/`Small` (ADR-0034), resolved per family in
`FlashcardsProgressBarMetrics.kt`:

| | Normal | Small |
|---|---|---|
| Linear/segmented track height | 5dp (`progressBarThicknessNormal`) | 4dp (`progressBarThicknessSmall`) |
| Circular stroke width | 5dp (same token) | 4dp (same token) |
| Circular diameter | 56dp (`progressRingDiameterNormal`) | 40dp (`progressRingDiameterSmall`) |

Linear/segmented thickness and circular stroke width share one `AppSizes` token pair rather than
each having their own — the design uses the same values for both, and duplicating the pair would
only invite them drifting apart. Segment gap (`spacing.xxsmall`, 4dp) is a single value across
both tiers, not per-size — no `AppSpacing` token exists below `xxsmall` to derive a smaller
`Small`-tier gap from, so `Small` reuses the same 4dp rather than inventing one. Revisit if a real
`Small` segmented screen shows the gap reading too heavy against the 4dp `Small` segment height.

**Colors** — new `BrandColors` fields, mirroring ADR-0033's Tonal-button pattern of one
theme-fixed member alongside one theme-flipping member:

| Style | Fill | Track |
|---|---|---|
| `OnSurface` | `progressBarFillOnSurface` — **fixed** across themes, reuses the existing `ctaButtonGradient` purple stop (`#7C3FC4`) rather than `colorScheme.primary`, so the Surface and Gradient treatments stay the same brand purple in dark mode instead of drifting apart | `progressBarTrackOnSurface` — **theme-flipping**: `surfaceContainerHighLight` in light, `surfaceContainerHighDark` in dark (same dark token `tonalButtonContainer` uses, for the same hue-consistency reason) |
| `OnGradient` | `progressBarFillOnGradient` — fixed white | `progressBarTrackOnGradient` — fixed white @ 20% alpha |

Both `OnGradient` members are theme-fixed like every other on-gradient color in `BrandColors` —
the gradients they sit on never flip either.

**Animation.** `FlashcardsLinearProgressBar`/`FlashcardsCircularProgressRing` animate `progress`
via `animateFloatAsState`, using a shared `flashcardsProgressBarDefaultAnimationSpec`
(`FlashcardsMotion.DURATION_MEDIUM_MS` + standard easing). `FlashcardsSegmentedProgressBar` has no
animation — segments are a hard color swap, no interpolated value.

Both drawn in a single `Canvas` (`drawRoundRect`/`drawArc`), animated value read only inside the
draw lambda rather than hoisted with `by` — keeps a tick a draw-only invalidation, not a
recomposition. `animate: Boolean = true` (not an `AnimationSpec` param — that type is unstable in
`androidx.compose.animation.core`) is the escape hatch for noisy/frequent `progress` updates,
passing `snap()` internally when `false`.

**Clamping, not crashing.** `progress` is coerced to `0f..1f`; `FlashcardsSegmentedProgressBar`
coerces `segmentCount` to at least 1 and `filledSegmentCount` to `0..segmentCount`. These render
real (possibly noisy) domain data rather than throwing on it.

**Accessibility.** All three set `Modifier.semantics { progressBarRangeInfo = ... }` internally,
unconditionally — not optional, since these wrap a bare `Canvas`/`Box` instead of M3's own
`LinearProgressIndicator`/`CircularProgressIndicator`, which would otherwise supply this for free.

**Showkase group:** a new `"Progress"` group, not `"Feedback"` (an existing but unrelated group in
this codebase's flat, role-based taxonomy).

## Consequences

- `BrandColors` gains four new fields (`progressBarFillOnSurface`, `progressBarTrackOnSurface`,
  `progressBarFillOnGradient`, `progressBarTrackOnGradient`); `AppSizes` gains four
  (`progressBarThicknessNormal/Small`, `progressRingDiameterNormal/Small`). **Superseded:** the
  `progressRingDiameter*` tokens were removed, see the final Amendment.
- Any composite component that embeds one of these bars (Level/XP card, "Continue learning" card,
  list-row rings, the 88dp daily-goal ring) is future work, built against real domain data once
  those screens are reached — not designed speculatively here.
- Exact `progressBarTrackOnSurface`/`OnGradient` alpha values may be revisited once a concrete
  screen renders these on a real background (mirrors the same caveat in ADR-0033).

## Amendment: one ring, the list-row ring

`FlashcardsCircularProgressRing` never gained a production caller, while the list-row
`FlashcardsProgressRing` (Browse search and Category Details Topic rows) shipped with its own look:
3dp stroke, `colorScheme.primary` fill over a 16% `primary` track, butt ends, no animation, and a
built-in percentage plus a nullable unknown state. Two rings with different looks was one too many,
so the unused generic ring, its metrics and the `progressRingDiameterNormal/Small` tokens are
removed and `FlashcardsProgressRing` is the only ring. The progress family above is now linear and
segmented; a future ring use (the "Continue learning" card, the daily-goal ring) extends
`FlashcardsProgressRing` against its real design rather than reviving the generic one.
