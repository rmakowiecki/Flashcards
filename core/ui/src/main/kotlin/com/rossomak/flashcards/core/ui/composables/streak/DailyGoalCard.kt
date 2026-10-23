package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.semanticColors
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
private const val DAILY_GOAL_CHECK_SCALE = 1.5f
private const val PERCENT = 100

/**
 * Today's Daily Goal figures as they should read. [isStudiedUnderAMinute] shows the minutes as "<1"; [isGoalMet]
 * means today's Daily Goal XP is earned, and swaps the percent for a check inside a full green ring.
 */
data class DailyGoalProgress(val studiedMinutes: Int, val isStudiedUnderAMinute: Boolean, val goalMinutes: Int, val percent: Int, val isGoalMet: Boolean)

/** Read-only Daily Goal card. */
@Composable
fun DailyGoalCard(progress: DailyGoalProgress, modifier: Modifier = Modifier) = with(progress) {
    val animationsEnabled = rememberAnimationsEnabled()
    val shownProgress by animateFloatAsState(
        targetValue = if (isGoalMet) 1f else percent.coerceIn(0, PERCENT).toFloat() / PERCENT,
        animationSpec = revealSpec(animationsEnabled),
        label = "DailyGoalCardProgress",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (isGoalMet) 1f else 0f,
        animationSpec = revealSpec(animationsEnabled),
        label = "DailyGoalCardCheckAlpha",
    )
    val arcColor by animateColorAsState(
        targetValue = if (isGoalMet) MaterialTheme.semanticColors.onPositiveContainer else MaterialTheme.colorScheme.primary,
        animationSpec = revealSpec(animationsEnabled),
        label = "DailyGoalCardArcColor",
    )
    val description = when {
        isGoalMet -> pluralStringResource(R.plurals.daily_goal_card_met_cd, goalMinutes, studiedMinutes, goalMinutes)
        isStudiedUnderAMinute -> pluralStringResource(R.plurals.daily_goal_card_under_minute_cd, goalMinutes, goalMinutes)
        else -> pluralStringResource(R.plurals.daily_goal_card_cd, goalMinutes, studiedMinutes, goalMinutes)
    }

    DailyProgressCardFrame(
        title = stringResource(R.string.daily_goal_card_title),
        description = description,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DailyGoalRing(sweep = { shownProgress }, arcColor = arcColor) {
                DailyGoalRingLabel(percent = if (isGoalMet) PERCENT else percent, checkAlpha = { checkAlpha }, checkColor = arcColor)
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall)) {
                    Text(
                        text = when {
                            isStudiedUnderAMinute -> stringResource(R.string.daily_goal_card_under_minute_count_label)
                            else -> stringResource(R.string.daily_progress_card_count_label, studiedMinutes)
                        },
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

@Composable
private fun <T> revealSpec(animationsEnabled: Boolean): AnimationSpec<T> = remember(animationsEnabled) {
    when {
        animationsEnabled -> tween(durationMillis = FlashcardsMotion.REVEAL_DURATION_MS, easing = FlashcardsMotion.EmphasizedEasing)
        else -> snap()
    }
}

/**
 * The percent crossfading into the check. The percent keeps its space while hidden and the check never takes
 * any, so the ring is the same size met or not, at every font scale.
 */
@Composable
private fun DailyGoalRingLabel(percent: Int, checkAlpha: () -> Float, checkColor: Color) {
    val checkSize = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() } * DAILY_GOAL_CHECK_SCALE
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.daily_goal_card_percent_label, percent),
            modifier = Modifier.graphicsLayer { alpha = 1f - checkAlpha() },
            style = MaterialTheme.typography.labelSmall,
        )
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.requiredSize(checkSize).graphicsLayer { alpha = checkAlpha() },
            tint = checkColor,
        )
    }
}

/** A round ring that grows to hold [content], never capped, so large text stays inside it; [sweep] is read only while drawing, so it never recomposes. */
@Composable
private fun DailyGoalRing(sweep: () -> Float, arcColor: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
                val fraction = sweep()
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
                PreviewDailyGoalCard(studiedMinutes = 14, percent = 70)
                PreviewDailyGoalCard(studiedMinutes = 19, percent = 99)
                PreviewDailyGoalCard(studiedMinutes = 28, percent = 100, isGoalMet = true)
                PreviewDailyGoalCard(studiedMinutes = 0, percent = 3, isStudiedUnderAMinute = true)
                PreviewDailyGoalCard(studiedMinutes = 0, percent = 0)
            }
        }
    }
}

@Composable
private fun PreviewDailyGoalCard(studiedMinutes: Int, percent: Int, isGoalMet: Boolean = false, isStudiedUnderAMinute: Boolean = false) {
    DailyGoalCard(
        progress = DailyGoalProgress(
            studiedMinutes = studiedMinutes,
            isStudiedUnderAMinute = isStudiedUnderAMinute,
            goalMinutes = 20,
            percent = percent,
            isGoalMet = isGoalMet,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
