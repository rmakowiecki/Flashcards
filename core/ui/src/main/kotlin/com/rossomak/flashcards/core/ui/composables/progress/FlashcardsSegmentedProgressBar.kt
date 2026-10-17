package com.rossomak.flashcards.core.ui.composables.progress

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * Discrete-step progress (onboarding-style: N equal segments, each either filled or unfilled —
 * no third "in progress" state for the current step). A bare row of bars, no text, no card
 * chrome, same as [FlashcardsLinearProgressBar]. See ADR-0035.
 *
 * [segmentCount] is coerced to at least 1 and [filledSegmentCount] is clamped to
 * `0..segmentCount` rather than throwing — this renders real (possibly noisy) domain data rather
 * than crashing on it.
 *
 * Drawn as one [Canvas] pass rather than `segmentCount` separate `Box` children — one layout node
 * regardless of segment count, matching the single-`Canvas` approach the other two progress
 * composables use (ADR-0035). No animation here (segments are a hard color swap, not an
 * interpolated value), so this file has none of [FlashcardsLinearProgressBar]'s
 * draw-phase-vs-composition-phase concerns.
 */
@Composable
fun FlashcardsSegmentedProgressBar(
    segmentCount: Int,
    filledSegmentCount: Int,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
) {
    val safeSegmentCount = clampSegmentCount(segmentCount)
    val clampedFilledCount = clampFilledSegmentCount(filledSegmentCount, safeSegmentCount)
    val metrics = size.segmentedProgressBarMetrics()
    val fillColor = progressBarFillColorFor(style)
    val trackColor = progressBarTrackColorFor(style)
    val gap = metrics.segmentGap

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.segmentHeight)
            .semantics {
                // steps = safeSegmentCount - 1: this is discrete step progress, not a continuous
                // range, so TalkBack announces segment granularity instead of treating it as smooth.
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clampedFilledCount.toFloat(),
                    range = 0f..safeSegmentCount.toFloat(),
                    steps = safeSegmentCount - 1,
                )
            },
    ) {
        // this.size (DrawScope's own size) — bare `size` resolves to the outer
        // FlashcardsComponentSize param here instead, see FlashcardsLinearProgressBar.kt.
        val gapPx = gap.toPx()
        val totalGapPx = gapPx * (safeSegmentCount - 1)
        val segmentWidthPx = (this.size.width - totalGapPx) / safeSegmentCount
        val corner = CornerRadius(this.size.height / 2f)

        repeat(safeSegmentCount) { index ->
            val left = index * (segmentWidthPx + gapPx)
            drawRoundRect(
                color = if (index < clampedFilledCount) fillColor else trackColor,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidthPx, this.size.height),
                cornerRadius = corner,
            )
        }
    }
}

@ShowkaseComposable(name = "Segmented", group = "Progress")
@Composable
fun FlashcardsSegmentedProgressBarShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSegmentedProgressBar(
                segmentCount = 8,
                filledSegmentCount = 4,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Segmented — small", group = "Progress")
@Composable
fun FlashcardsSegmentedProgressBarSmallShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSegmentedProgressBar(
                segmentCount = 8,
                filledSegmentCount = 4,
                size = FlashcardsComponentSize.Small,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

@ShowkaseComposable(name = "Segmented — on gradient", group = "Progress")
@Composable
fun FlashcardsSegmentedProgressBarOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.ctaButtonGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            FlashcardsSegmentedProgressBar(
                segmentCount = 8,
                filledSegmentCount = 4,
                style = FlashcardsComponentStyle.OnGradient,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsSegmentedProgressBarPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsSegmentedProgressBar(segmentCount = 8, filledSegmentCount = 4)
                FlashcardsSegmentedProgressBar(
                    segmentCount = 8,
                    filledSegmentCount = 4,
                    size = FlashcardsComponentSize.Small,
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.brandColors.ctaButtonGradient)
                        .padding(MaterialTheme.spacing.small),
                ) {
                    FlashcardsSegmentedProgressBar(
                        segmentCount = 8,
                        filledSegmentCount = 4,
                        style = FlashcardsComponentStyle.OnGradient,
                    )
                }
            }
        }
    }
}
