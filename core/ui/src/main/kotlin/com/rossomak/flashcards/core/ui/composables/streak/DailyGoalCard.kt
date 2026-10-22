package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.animation.rememberAnimationsEnabled
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

private val DAILY_GOAL_RING_STROKE = 4.dp

private const val DAILY_GOAL_RING_FULL_CIRCLE_DEGREES = 360f
private const val DAILY_GOAL_RING_START_DEGREES = -90f
private const val DAILY_GOAL_RING_TRACK_ALPHA = 0.16f
private const val PERCENT = 100

/**
 * Read-only Daily Goal card. The caller passes minutes already rounded and [progress] already capped at 1.
 * A full ring never means the goal was met: the server rounds the minutes down when awarding the Daily Goal XP.
 */
@Composable
fun DailyGoalCard(studiedMinutes: Int, goalMinutes: Int, progress: Float, modifier: Modifier = Modifier) {
    val animationsEnabled = rememberAnimationsEnabled()
    val animationSpec = remember(animationsEnabled) {
        when {
            animationsEnabled -> tween<Float>(durationMillis = FlashcardsMotion.REVEAL_DURATION_MS, easing = FlashcardsMotion.EmphasizedEasing)
            else -> snap()
        }
    }
    val shownProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "DailyGoalCardProgress",
    )

    DailyProgressCardFrame(
        title = stringResource(R.string.daily_goal_card_title),
        description = pluralStringResource(R.plurals.daily_goal_card_cd, goalMinutes, studiedMinutes, goalMinutes),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DailyGoalRing(progress = { shownProgress }) {
                Text(
                    text = stringResource(R.string.daily_goal_card_percent_label, dailyGoalPercent(studiedMinutes, goalMinutes)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall)) {
                    Text(
                        text = stringResource(R.string.daily_progress_card_count_label, studiedMinutes),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.daily_goal_card_unit_label),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.daily_goal_card_goal_label, goalMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Rounded down, so 100% shows only once the minutes reach the goal. */
internal fun dailyGoalPercent(studiedMinutes: Int, goalMinutes: Int): Int = when {
    goalMinutes > 0 -> (studiedMinutes * PERCENT / goalMinutes).coerceIn(0, PERCENT)
    else -> 0
}

/** A round ring that grows to hold [content], never capped, so large text stays inside it; [progress] is read only while drawing, so the sweep never recomposes. */
@Composable
private fun DailyGoalRing(progress: () -> Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val arcColor = MaterialTheme.colorScheme.primary
    val trackColor = arcColor.copy(alpha = DAILY_GOAL_RING_TRACK_ALPHA)

    Layout(
        content = content,
        modifier = modifier.drawWithCache {
            val stroke = Stroke(width = DAILY_GOAL_RING_STROKE.toPx(), cap = StrokeCap.Round)
            // Inset by half the stroke so the ring is drawn inside its bounds rather than clipped.
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            onDrawBehind {
                drawArc(
                    color = trackColor,
                    startAngle = DAILY_GOAL_RING_START_DEGREES,
                    sweepAngle = DAILY_GOAL_RING_FULL_CIRCLE_DEGREES,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                val fraction = progress()
                if (fraction > 0f) {
                    drawArc(
                        color = arcColor,
                        startAngle = DAILY_GOAL_RING_START_DEGREES,
                        sweepAngle = DAILY_GOAL_RING_FULL_CIRCLE_DEGREES * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            }
        },
        measurePolicy = DailyGoalRingMeasurePolicy,
    )
}

/**
 * Sizes the ring from its content's own size. Explicit intrinsics keep a parent's `IntrinsicSize.Min` from
 * measuring the content at the full available width, which would blow the ring up.
 */
private object DailyGoalRingMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val diameter = ringDiameter(contentWidth = placeable.width, contentHeight = placeable.height)
        return layout(diameter, diameter) {
            placeable.place(x = (diameter - placeable.width) / 2, y = (diameter - placeable.height) / 2)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = intrinsicDiameter(measurables)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = intrinsicDiameter(measurables)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = intrinsicDiameter(measurables)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = intrinsicDiameter(measurables)

    private fun IntrinsicMeasureScope.intrinsicDiameter(measurables: List<IntrinsicMeasurable>): Int {
        val content = measurables.single()
        val contentWidth = content.maxIntrinsicWidth(Constraints.Infinity)
        return ringDiameter(contentWidth = contentWidth, contentHeight = content.minIntrinsicHeight(contentWidth))
    }

    private fun Density.ringDiameter(contentWidth: Int, contentHeight: Int): Int = dailyGoalRingDiameter(
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        strokeWidth = DAILY_GOAL_RING_STROKE.roundToPx(),
        minDiameter = DAILY_PROGRESS_CARD_VISUAL_SIZE.roundToPx(),
    )
}

/** The smallest diameter, never below [minDiameter], whose ring inner edge fits the content's diagonal. */
internal fun dailyGoalRingDiameter(contentWidth: Int, contentHeight: Int, strokeWidth: Int, minDiameter: Int): Int {
    val contentDiagonal = ceil(hypot(contentWidth.toDouble(), contentHeight.toDouble())).toInt()
    return max(minDiameter, contentDiagonal + 2 * strokeWidth)
}

@PreviewLightDark
@Composable
private fun DailyGoalCardPreview() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                DailyGoalCard(studiedMinutes = 14, goalMinutes = 20, progress = 0.7f, modifier = Modifier.fillMaxWidth())
                DailyGoalCard(studiedMinutes = 25, goalMinutes = 20, progress = 1f, modifier = Modifier.fillMaxWidth())
                DailyGoalCard(studiedMinutes = 0, goalMinutes = 20, progress = 0f, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
