package com.rossomak.flashcards.core.ui.composables.progress

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.AppSpacing
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Geometry for [FlashcardsLinearProgressBar]: just the track/fill height. */
internal data class FlashcardsLinearProgressBarMetrics(
    val trackHeight: Dp,
)

/** Geometry for [FlashcardsSegmentedProgressBar]: per-segment height plus the inter-segment gap. */
internal data class FlashcardsSegmentedProgressBarMetrics(
    val segmentHeight: Dp,
    val segmentGap: Dp,
)

/**
 * Track height shared by the linear and segmented bars at the given [FlashcardsComponentSize].
 */
@Composable
@ReadOnlyComposable
private fun FlashcardsComponentSize.thickness(sizes: AppSizes = MaterialTheme.sizes): Dp = when (this) {
    FlashcardsComponentSize.Normal -> sizes.progressBarThicknessNormal
    FlashcardsComponentSize.Small -> sizes.progressBarThicknessSmall
}

@Composable
@ReadOnlyComposable
internal fun FlashcardsComponentSize.linearProgressBarMetrics(): FlashcardsLinearProgressBarMetrics =
    FlashcardsLinearProgressBarMetrics(trackHeight = thickness())

/**
 * Segment gap is a single value across both size tiers, not a per-tier token — `spacing.xxsmall`
 * (4dp) is reused as-is at `Small`, since no design token exists below it to derive a smaller gap
 * from. Revisit if a real `Small` segmented screen shows the gap reading too heavy against the
 * 4dp `Small` segment height.
 */
@Composable
@ReadOnlyComposable
internal fun FlashcardsComponentSize.segmentedProgressBarMetrics(
    spacing: AppSpacing = MaterialTheme.spacing,
): FlashcardsSegmentedProgressBarMetrics = FlashcardsSegmentedProgressBarMetrics(
    segmentHeight = thickness(),
    segmentGap = spacing.xxsmall,
)

/** Fill color for the given [style], shared by every `Flashcards*Progress*` composable. */
@Composable
@ReadOnlyComposable
internal fun progressBarFillColorFor(style: FlashcardsComponentStyle): Color = when (style) {
    FlashcardsComponentStyle.OnSurface -> MaterialTheme.brandColors.progressBarFillOnSurface
    FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.progressBarFillOnGradient
}

/** Track (unfilled) color for the given [style], shared by every `Flashcards*Progress*` composable. */
@Composable
@ReadOnlyComposable
internal fun progressBarTrackColorFor(style: FlashcardsComponentStyle): Color = when (style) {
    FlashcardsComponentStyle.OnSurface -> MaterialTheme.brandColors.progressBarTrackOnSurface
    FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.progressBarTrackOnGradient
}

/**
 * Clamps [progress] into the renderable `0f..1f` range rather than crashing on out-of-range input.
 *
 * `Float.coerceIn` does not catch `NaN`: IEEE 754 comparisons (`<`/`>`) against `NaN` are always
 * `false`, so `NaN.coerceIn(0f, 1f)` returns `NaN` unchanged, not a clamped value. Checked
 * explicitly here and mapped to `0f` (empty, not "complete") — upstream `NaN` (e.g. a `0/0`
 * percentage calculation) should render as no progress, not silently propagate into
 * `animateFloatAsState`/`ProgressBarRangeInfo`.
 */
internal fun clampProgressBarProgress(progress: Float): Float =
    if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/** Segment counts render at most this many segments — see [clampSegmentCount]. */
private const val MAX_SEGMENT_COUNT = 100

/**
 * Clamps [segmentCount] into `1..`[MAX_SEGMENT_COUNT] — a zero/negative count renders as a single
 * segment rather than crashing; the upper bound guards against a pathological/corrupted count
 * (e.g. `Int.MAX_VALUE`) driving billions of `Canvas` draw calls on the UI thread. No real
 * segmented-progress design needs anywhere near 100 steps.
 */
internal fun clampSegmentCount(segmentCount: Int): Int = segmentCount.coerceIn(1, MAX_SEGMENT_COUNT)

/** Clamps [filledSegmentCount] into `0..segmentCount` rather than crashing on noisy domain data. */
internal fun clampFilledSegmentCount(filledSegmentCount: Int, segmentCount: Int): Int =
    filledSegmentCount.coerceIn(0, segmentCount)

/**
 * Default fill-transition spec for [FlashcardsLinearProgressBar] — reuses [FlashcardsMotion]'s standard medium duration/easing rather than a bespoke value.
 *
 * Not part of either composable's public signature — `AnimationSpec<T>` carries no `@Stable`/
 * `@Immutable` annotation in `androidx.compose.animation.core`, so exposing it as a parameter type
 * would make both composables non-skippable on *every* recomposition of their caller, not just
 * during an animation. Both instead expose a plain `animate: Boolean` (always stable) and pass
 * `snap()` internally when `false` — the one realistic escape hatch (skip the transition for
 * noisy/frequently-updating `progress`) without the stability cost. See ADR-0035.
 */
internal val flashcardsProgressBarDefaultAnimationSpec: AnimationSpec<Float> = tween(
    durationMillis = FlashcardsMotion.DURATION_MEDIUM_MS,
    easing = FlashcardsMotion.StandardEasing,
)
