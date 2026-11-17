package com.rossomak.flashcards.web.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How the site's components appear: each fades in while it rises [rise] into place over [duration],
 * and in a sequence each next one starts [stagger] after the one before.
 */
@Immutable
data class RevealMotion(val duration: Duration, val rise: Dp, val stagger: Duration) {
    internal fun tween() = tween<Float>(durationMillis = duration.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.EmphasizedDecelerateEasing)

    companion object {
        val Default = RevealMotion(duration = 450.milliseconds, rise = 16.dp, stagger = 80.milliseconds)
    }
}

/** The reveal motion of the site, which the landing page's local tuner changes live. */
val LocalRevealMotion = staticCompositionLocalOf { RevealMotion.Default }

/** The large logos start this much smaller as they reveal, and grow into place. */
const val LOGO_REVEAL_START_SCALE = 0.96f

/**
 * Components that reveal one after another, by slot: slot n starts n staggers after slot 0. A slot's
 * progress runs from 0 (hidden) to 1 (in place).
 */
@Stable
class RevealSequence internal constructor(slotCount: Int, initial: Float) {
    private val slots: List<Animatable<Float, AnimationVector1D>> = List(slotCount) { Animatable(initial) }

    fun progress(slot: Int): Float = slots[slot].value

    internal suspend fun play(motion: RevealMotion): Unit = coroutineScope {
        slots.forEachIndexed { slot, progress ->
            launch {
                delay(motion.stagger * slot)
                progress.animateTo(1f, motion.tween())
            }
        }
    }

    internal suspend fun snapTo(value: Float) = slots.forEach { progress -> progress.snapTo(value) }
}

/**
 * A [RevealSequence] of [slotCount] slots that plays each time [shown] turns true and hides at once when
 * it turns false. Under the browser's reduced-motion preference every slot stays in place.
 */
@Composable
fun rememberRevealSequence(slotCount: Int, shown: Boolean): RevealSequence {
    val reducedMotion by rememberPrefersReducedMotion()
    val motion by rememberUpdatedState(LocalRevealMotion.current)
    val sequence = remember(slotCount) { RevealSequence(slotCount, initial = if (reducedMotion) 1f else 0f) }
    LaunchedEffect(sequence, shown, reducedMotion) {
        when {
            reducedMotion -> sequence.snapTo(1f)
            shown -> sequence.play(motion)
            else -> sequence.snapTo(0f)
        }
    }
    return sequence
}

/** Fades this in and lifts it [rise] into place as [progress] goes from 0 to 1, growing from [startScale]. */
fun Modifier.reveal(progress: () -> Float, rise: Dp, startScale: Float = 1f): Modifier = graphicsLayer {
    val fraction = progress()
    alpha = fraction
    translationY = (1f - fraction) * rise.toPx()
    if (startScale != 1f) {
        scaleX = lerp(startScale, 1f, fraction)
        scaleY = scaleX
    }
}

/** Share of the window's height the top of a [RevealWhenScrolledIn] block must scroll above before it reveals. */
private const val SCROLL_IN_WINDOW_SHARE = 0.92f

/**
 * [content] that reveals once, the first time it scrolls into the window. Content already in the window
 * when it first lays out shows at once, as part of whatever reveals around it.
 */
@Composable
fun RevealWhenScrolledIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reducedMotion by rememberPrefersReducedMotion()
    val motion = LocalRevealMotion.current
    // Null until first laid out, then whether it was in view then; true once it has scrolled in.
    var inViewOnFirstLayout by remember { mutableStateOf<Boolean?>(null) }
    var scrolledIn by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(scrolledIn) {
        when {
            !scrolledIn -> Unit
            reducedMotion || inViewOnFirstLayout == true -> progress.snapTo(1f)
            else -> progress.animateTo(1f, motion.tween())
        }
    }
    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                if (scrolledIn) return@onGloballyPositioned
                val windowHeight = coordinates.findRootCoordinates().size.height
                val inView = coordinates.positionInRoot().y < windowHeight * SCROLL_IN_WINDOW_SHARE
                if (inViewOnFirstLayout == null) inViewOnFirstLayout = inView
                if (inView) scrolledIn = true
            }
            .reveal(progress = { if (inViewOnFirstLayout == null) 1f else progress.value }, rise = motion.rise),
    ) {
        content()
    }
}
