package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.animation.rememberAnimationsEnabled
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

private val DAILY_GOAL_RING_MIN_DIAMETER = 88.dp
private val DAILY_GOAL_RING_STROKE = 6.dp

private const val DAILY_GOAL_RING_FULL_CIRCLE_DEGREES = 360f
private const val DAILY_GOAL_RING_START_DEGREES = -90f
private const val DAILY_GOAL_RING_TRACK_ALPHA = 0.16f

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
        DailyGoalRing(
            progress = { shownProgress },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = studiedMinutes.toString(),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = stringResource(R.string.daily_goal_card_goal_label, goalMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.daily_goal_card_unit_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A round ring sized to hold [content]; [progress] is read only while drawing, so the sweep never recomposes. */
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
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val diameter = dailyGoalRingDiameter(
            contentWidth = placeable.width,
            contentHeight = placeable.height,
            strokeWidth = DAILY_GOAL_RING_STROKE.roundToPx(),
            minDiameter = DAILY_GOAL_RING_MIN_DIAMETER.roundToPx(),
        ).coerceAtMost(minOf(constraints.maxWidth, constraints.maxHeight))
        layout(diameter, diameter) {
            placeable.place(x = (diameter - placeable.width) / 2, y = (diameter - placeable.height) / 2)
        }
    }
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
