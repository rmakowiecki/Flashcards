package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val PARTICLE_COUNT = 34
private const val ANGLE_STEP_OFFSET = 0.21
private const val BASE_DISTANCE_DP = 90
private const val DISTANCE_STEP_DP = 34
private const val LIFT_DP = 30
private const val BASE_ROTATION_DEGREES = 180
private const val ROTATION_STEP_DEGREES = 120
private const val BASE_SIZE_DP = 6
private const val SIZE_STEP_DP = 3
private const val SMALL_CORNER_DP = 2f
private const val ANGLE_VARIANTS = 3
private const val DISTANCE_VARIANTS = 5
private const val ROTATION_VARIANTS = 4
private const val SIZE_VARIANTS = 3
private const val ROUND_EVERY = 4
private const val DELAY_VARIANTS = 6
private const val DURATION_VARIANTS = 5
private const val DELAY_STEP_MS = 36
private const val BASE_DURATION_MS = 1_100
private const val DURATION_STEP_MS = 150
private const val START_SCALE = 0.4f
private const val FADE_IN_END = 0.12f
private const val FADE_OUT_START = 0.7f

/** Decorative and theme-independent, like the brand gradient: a burst looks the same in light and dark. */
private val ConfettiColors = listOf(
    Color(0xFFFFD54F),
    Color(0xFFFF8A65),
    Color(0xFFA5D6A7),
    Color(0xFF90CAF9),
    Color(0xFFCE93D8),
    Color(0xFFFFE082),
    Color(0xFF80CBC4),
)

private val ConfettiEasing = CubicBezierEasing(0.15f, 0.6f, 0.4f, 1f)

/** One deterministic particle: where it ends up (dp from the origin), how it spins and when it flies. */
private class ConfettiParticle(
    val travel: Offset,
    val rotationDegrees: Float,
    val sizeDp: Float,
    val isRound: Boolean,
    val delay: Duration,
    val duration: Duration,
    val color: Color,
)

private val Particles = List(PARTICLE_COUNT) { index ->
    val angle = 2 * PI * index / PARTICLE_COUNT + (index % ANGLE_VARIANTS) * ANGLE_STEP_OFFSET
    val distance = BASE_DISTANCE_DP + (index % DISTANCE_VARIANTS) * DISTANCE_STEP_DP
    val spin = BASE_ROTATION_DEGREES + (index % ROTATION_VARIANTS) * ROTATION_STEP_DEGREES
    ConfettiParticle(
        travel = Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance - LIFT_DP).toFloat()),
        rotationDegrees = (if (index % 2 == 1) spin else -spin).toFloat(),
        sizeDp = (BASE_SIZE_DP + (index % SIZE_VARIANTS) * SIZE_STEP_DP).toFloat(),
        isRound = index % ROUND_EVERY == 0,
        delay = ((index % DELAY_VARIANTS) * DELAY_STEP_MS).milliseconds,
        duration = (BASE_DURATION_MS + (index % DURATION_VARIANTS) * DURATION_STEP_MS).milliseconds,
        color = ConfettiColors[index % ConfettiColors.size],
    )
}

/** From the burst until the last particle has finished. */
private val BurstDuration: Duration = Particles.maxOf { it.delay + it.duration }

/**
 * One burst of confetti from [origin] (window coordinates), drawn on a single canvas over the screen.
 * It draws nothing while [origin] is `null` and plays once when it becomes non-null. The canvas only
 * draws, so it consumes no touches, and every particle runs its full duration: the burst is owned by
 * this composable, not by the sequence that started it.
 */
@Composable
internal fun SummaryConfetti(origin: Offset?, modifier: Modifier = Modifier) {
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var isBursting by remember { mutableStateOf(false) }
    val elapsed = remember { Animatable(0f) }

    LaunchedEffect(origin) {
        if (origin == null) return@LaunchedEffect
        isBursting = true
        elapsed.snapTo(0f)
        elapsed.animateTo(BurstDuration.inWholeMilliseconds.toFloat(), tween(BurstDuration.toMillisInt(), easing = LinearEasing))
        isBursting = false
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> canvasOrigin = coordinates.positionInWindow() },
    ) {
        if (origin != null && isBursting) drawBurst(origin - canvasOrigin, elapsed.value)
    }
}

private fun DrawScope.drawBurst(center: Offset, elapsedMillis: Float) {
    Particles.forEach { particle ->
        val progress = ((elapsedMillis - particle.delay.inWholeMilliseconds) / particle.duration.inWholeMilliseconds).coerceIn(0f, 1f)
        if (progress <= 0f || progress >= 1f) return@forEach
        val eased = ConfettiEasing.transform(progress)
        val alpha = when {
            progress < FADE_IN_END -> progress / FADE_IN_END
            progress < FADE_OUT_START -> 1f
            else -> 1f - (progress - FADE_OUT_START) / (1f - FADE_OUT_START)
        }
        val position = center + Offset(particle.travel.x.dp.toPx(), particle.travel.y.dp.toPx()) * eased
        val side = particle.sizeDp.dp.toPx()
        val scaleFactor = START_SCALE + (1f - START_SCALE) * eased
        rotate(degrees = particle.rotationDegrees * eased, pivot = position) {
            scale(scale = scaleFactor, pivot = position) {
                val corner = if (particle.isRound) side / 2f else SMALL_CORNER_DP.dp.toPx()
                drawRoundRect(
                    color = particle.color,
                    topLeft = position - Offset(side / 2f, side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(corner),
                    alpha = alpha,
                )
            }
        }
    }
}
