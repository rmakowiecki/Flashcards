package com.rossomak.flashcards.web.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion

private const val HOVER_MILLIS = 150

/**
 * How far the pointer's hover over a component whose `Modifier.hoverable` takes this source has eased
 * in: 0 at rest, 1 while hovered. Under the browser's reduced-motion preference it changes at once.
 */
@Composable
fun MutableInteractionSource.hoverProgress(): State<Float> {
    val hovered by collectIsHoveredAsState()
    val reducedMotion by rememberPrefersReducedMotion()
    return animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(HOVER_MILLIS, easing = FlashcardsMotion.StandardEasing),
        label = "hover",
    )
}

/** [content] in the surrounding content color at [restAlpha] of its alpha, rising to the full color while hovered. */
@Composable
fun HoverBrightens(modifier: Modifier = Modifier, restAlpha: Float, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hover by interactionSource.hoverProgress()
    val color = LocalContentColor.current
    Box(modifier = modifier.hoverable(interactionSource)) {
        CompositionLocalProvider(LocalContentColor provides color.copy(alpha = color.alpha * lerp(restAlpha, 1f, hover))) {
            content()
        }
    }
}
