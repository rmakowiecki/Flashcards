package com.rossomak.flashcards.core.ui.composables.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The continuous linear progress bar — a bare track + fill, no text, no card chrome. Covers every
 * continuous-progress use case (session progress, XP/level bars): callers compose their own
 * label/count `Text` around it. Use [FlashcardsSegmentedProgressBar] instead for discrete-step
 * progress (onboarding). See ADR-0035.
 *
 * Drawn as a single [Canvas] pass (track + fill both `drawRoundRect`) rather than two nested
 * `Box`es — matches M3's own `LinearProgressIndicator` implementation and, critically, keeps the
 * per-frame animated value a **draw-phase-only** read: [androidx.compose.animation.core.State.value]
 * is read inside the draw lambda, not delegated at the top of the function, so an in-flight
 * animation invalidates only this `Canvas`'s draw, never triggers recomposition or relayout of
 * this composable or its caller.
 */
@Composable
fun FlashcardsLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
    animate: Boolean = true,
) {
    val clampedProgress = clampProgressBarProgress(progress)
    // Not `by` — kept as a State<Float> and dereferenced only inside the Canvas draw lambda
    // below, so a running animation re-triggers just that draw, not composition. See ADR-0035.
    val animatedProgress = animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = if (animate) flashcardsProgressBarDefaultAnimationSpec else snap(),
        label = "FlashcardsLinearProgressBarProgress",
    )
    LinearProgressBarCanvas(
        progress = { animatedProgress.value },
        modifier = modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(clampedProgress, 0f..1f) },
        size = size,
        style = style,
    )
}

/**
 * [FlashcardsLinearProgressBar] for a `core:ui` component that animates the fill itself, frame by frame,
 * such as the Level card's pour: [progress] is read in the draw phase only, so each frame redraws the bar
 * and recomposes nothing. It is drawn exactly as given (clamped to `0..1`), with no easing of its own.
 * Internal so that screens never direct a bar's frames; they hand a component its states instead.
 *
 * It carries no progress semantics, since an in-flight value is not worth announcing: the caller
 * describes the progress to accessibility itself, typically on the enclosing component.
 */
@Composable
internal fun FlashcardsLinearProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
) {
    LinearProgressBarCanvas(
        progress = { clampProgressBarProgress(progress()) },
        modifier = modifier,
        size = size,
        style = style,
    )
}

@Composable
private fun LinearProgressBarCanvas(
    progress: () -> Float,
    modifier: Modifier,
    size: FlashcardsComponentSize,
    style: FlashcardsComponentStyle,
) {
    val trackHeight = size.linearProgressBarMetrics().trackHeight
    val fillColor = progressBarFillColorFor(style)
    val trackColor = progressBarTrackColorFor(style)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        // `this.size` (DrawScope's own size, the resolved canvas bounds) — bare `size` inside
        // this lambda resolves to the outer FlashcardsComponentSize param instead, since a
        // parameter from the enclosing function scope shadows the lambda receiver's member here.
        val corner = CornerRadius(this.size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = corner)
        val fillWidth = this.size.width * progress()
        if (fillWidth > 0f) {
            drawRoundRect(color = fillColor, size = Size(fillWidth, this.size.height), cornerRadius = corner)
        }
    }
}

@ShowkaseComposable(name = "Linear", group = "Progress")
@Composable
fun FlashcardsLinearProgressBarShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsLinearProgressBar(
                progress = 0.33f,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Linear — on gradient", group = "Progress")
@Composable
fun FlashcardsLinearProgressBarOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.ctaButtonGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            FlashcardsLinearProgressBar(
                progress = 0.33f,
                style = FlashcardsComponentStyle.OnGradient,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsLinearProgressBarPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsLinearProgressBar(progress = 0.33f)
                FlashcardsLinearProgressBar(progress = 0.7f, size = FlashcardsComponentSize.Small)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.ctaButtonGradient)
                        .padding(MaterialTheme.spacing.small),
                ) {
                    FlashcardsLinearProgressBar(progress = 0.568f, style = FlashcardsComponentStyle.OnGradient)
                }
            }
        }
    }
}
