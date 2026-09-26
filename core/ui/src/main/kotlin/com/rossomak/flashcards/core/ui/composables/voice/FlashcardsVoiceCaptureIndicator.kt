package com.rossomak.flashcards.core.ui.composables.voice

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.DEFAULT_CONTAINER_ALPHA
import com.rossomak.flashcards.core.ui.theme.AppSizes
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Icon disc with mirrored level bars on each side. The disc shows a microphone by default; pass a
 * speaker [icon] while the bars follow audio being played back rather than captured.
 *
 * [levels] is one snapshot of [FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT] values in `0..1`,
 * index 0 innermost. Each new snapshot is blended in linearly over [levelIntervalMillis], which
 * should match the producer's emission interval. The disc's size pulses independently of the level.
 *
 * While [isActive] is false only the disc shows and [levels] is ignored; turning it true fans the
 * bars out from the disc, and turning it false folds them back in. The indicator keeps its full
 * width either way, so the disc never moves. A changed [icon] crossfades in.
 */
@Composable
fun FlashcardsVoiceCaptureIndicator(
    levels: ImmutableList<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Mic,
    isActive: Boolean = true,
    levelIntervalMillis: Int = FlashcardsVoiceCaptureIndicatorDefaults.LEVEL_INTERVAL_MILLIS,
) {
    val levelBars = remember { LevelBars(levels) }
    LaunchedEffect(levels) { levelBars.animateTo(levels = levels, durationMillis = levelIntervalMillis) }
    // Read only while drawing, like the bar levels, so the fan animation redraws without recomposing.
    val fanOut = remember { Animatable(if (isActive) 1f else 0f) }
    LaunchedEffect(isActive) {
        fanOut.animateTo(
            targetValue = if (isActive) 1f else 0f,
            animationSpec = tween(durationMillis = FlashcardsMotion.DURATION_LONG_MS, easing = FlashcardsMotion.StandardEasing),
        )
    }

    val barColor = MaterialTheme.colorScheme.primary
    val barsModifier = Modifier.size(width = BarsAreaWidth, height = BarMaxHeight)

    Row(
        modifier = modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = barsModifier.drawBehind {
                drawLevelBars(levelBars = levelBars, fanOut = fanOut.value, color = barColor, growsTowardStart = true)
            },
        )
        PulsingIconDisc(icon = icon)
        Spacer(
            modifier = barsModifier.drawBehind {
                drawLevelBars(levelBars = levelBars, fanOut = fanOut.value, color = barColor, growsTowardStart = false)
            },
        )
    }
}

object FlashcardsVoiceCaptureIndicatorDefaults {
    /** Bars per side, and the expected size of a `levels` snapshot. */
    const val BAR_COUNT: Int = 5

    /** Expected interval between two `levels` snapshots. */
    const val LEVEL_INTERVAL_MILLIS: Int = 70

    /** Disc diameter, equal to a Rating circle so either can replace the other in place. */
    val discSize: Dp = AppSizes.ratingButton

    val discContentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSecondaryContainer

    /** Same tint as the default `FlashcardsIconTile` container. */
    val discContainerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = discContentColor.copy(alpha = DEFAULT_CONTAINER_ALPHA)
}

/**
 * Bar levels blended by a single progress animation. Values are read only while drawing, so an
 * animation frame redraws the bars without recomposing.
 */
private class LevelBars(initialLevels: ImmutableList<Float>) {
    private val startLevels = FloatArray(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT) { index ->
        initialLevels.levelAt(index)
    }
    private val targetLevels = startLevels.copyOf()
    private val progress = Animatable(1f)

    fun levelAt(index: Int): Float = lerp(startLevels[index], targetLevels[index], progress.value)

    /** Blends from the current bar levels, so an interrupted blend continues without a jump. */
    suspend fun animateTo(levels: ImmutableList<Float>, durationMillis: Int) {
        for (index in startLevels.indices) {
            startLevels[index] = levelAt(index)
            targetLevels[index] = levels.levelAt(index)
        }
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis, easing = LinearEasing))
    }
}

@Composable
private fun PulsingIconDisc(icon: ImageVector) {
    val pulse = rememberInfiniteTransition(label = "voiceCapturePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_HALF_CYCLE_MILLIS, easing = FlashcardsMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceCapturePulseFraction",
    )

    Box(
        modifier = Modifier.size(FlashcardsVoiceCaptureIndicatorDefaults.discSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val scale = lerp(1f, PULSE_MAX_SCALE, pulse.value)
                    scaleX = scale
                    scaleY = scale
                }
                .background(color = FlashcardsVoiceCaptureIndicatorDefaults.discContainerColor, shape = CircleShape),
        )
        Crossfade(targetState = icon, animationSpec = tween(FlashcardsMotion.DURATION_MEDIUM_MS), label = "voiceCaptureIcon") { shownIcon ->
            Icon(
                imageVector = shownIcon,
                // Decorative: the root node carries the announcement.
                contentDescription = null,
                tint = FlashcardsVoiceCaptureIndicatorDefaults.discContentColor,
            )
        }
    }
}

/**
 * Draws one side's bars; index 0 sits next to the disc. [fanOut] `0..1` slides each bar out from
 * index 0's slot to its own and fades it in, inner bars leading; at 0 nothing is drawn.
 */
private fun DrawScope.drawLevelBars(levelBars: LevelBars, fanOut: Float, color: Color, growsTowardStart: Boolean) {
    if (fanOut <= 0f) return
    val barWidth = BarWidth.toPx()
    val barStep = barWidth + BarGap.toPx()
    val restHeight = BarRestHeight.toPx()
    val maxHeight = size.height
    repeat(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT) { index ->
        val barFanOut = barFanOut(fanOut = fanOut, index = index)
        val level = levelBars.levelAt(index)
        val barHeight = lerp(restHeight, maxHeight * BAR_HEIGHT_ENVELOPE[index], level)
        val offsetFromDisc = index * barStep * barFanOut
        val barLeft = if (growsTowardStart) size.width - barWidth - offsetFromDisc else offsetFromDisc
        drawRoundRect(
            color = color,
            topLeft = Offset(x = barLeft, y = (maxHeight - barHeight) / 2f),
            size = Size(width = barWidth, height = barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
            alpha = lerp(BAR_MIN_ALPHA, 1f, level) * barFanOut,
        )
    }
}

/** One bar's share of the overall fan: each bar starts [FAN_STAGGER] later than the one inside it. */
private fun barFanOut(fanOut: Float, index: Int): Float {
    val barWindow = 1f - FAN_STAGGER * (FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT - 1)
    return ((fanOut - index * FAN_STAGGER) / barWindow).coerceIn(0f, 1f)
}

private fun ImmutableList<Float>.levelAt(index: Int): Float = getOrElse(index) { 0f }.coerceIn(0f, 1f)

private val BarWidth = 4.dp
private val BarGap = 5.dp
private val BarRestHeight = 6.dp
private val BarMaxHeight = 40.dp
private val BarsAreaWidth = BarWidth * FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT +
    BarGap * (FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT - 1)

/** Max height fraction per bar, innermost first. */
private val BAR_HEIGHT_ENVELOPE = floatArrayOf(1f, 0.96f, 0.9f, 0.82f, 0.74f)

private const val BAR_MIN_ALPHA = 0.35f
private const val PULSE_MAX_SCALE = 1.06f
private const val PULSE_HALF_CYCLE_MILLIS = 700
private const val FAN_STAGGER = 0.1f

private val RestLevels = persistentListOf(0f, 0f, 0f, 0f, 0f)
private val MidLevels = persistentListOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
private val PeakLevels = persistentListOf(1f, 1f, 1f, 1f, 1f)
private val WaveLevels = persistentListOf(0.35f, 0.9f, 0.6f, 0.2f, 0.05f)

@ShowkaseComposable(name = "Voice capture indicator", group = "Feedback")
@Composable
fun FlashcardsVoiceCaptureIndicatorShowcase() {
    VoiceCaptureIndicatorPreview(levels = WaveLevels)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorRestPreview() {
    VoiceCaptureIndicatorPreview(levels = RestLevels)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorMidPreview() {
    VoiceCaptureIndicatorPreview(levels = MidLevels)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorPeakPreview() {
    VoiceCaptureIndicatorPreview(levels = PeakLevels)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorWavePreview() {
    VoiceCaptureIndicatorPreview(levels = WaveLevels)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorPlaybackPreview() {
    VoiceCaptureIndicatorPreview(levels = WaveLevels, isPlayback = true)
}

@PreviewLightDark
@Composable
private fun FlashcardsVoiceCaptureIndicatorInactivePreview() {
    VoiceCaptureIndicatorPreview(levels = RestLevels, isActive = false)
}

@Composable
private fun VoiceCaptureIndicatorPreview(levels: ImmutableList<Float>, isPlayback: Boolean = false, isActive: Boolean = true) {
    FlashcardsTheme {
        Surface {
            FlashcardsVoiceCaptureIndicator(
                levels = levels,
                contentDescription = stringResource(
                    if (isPlayback) R.string.common_voice_capture_playing_cd else R.string.common_voice_capture_listening_cd,
                ),
                icon = if (isPlayback) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Mic,
                isActive = isActive,
            )
        }
    }
}
