package com.rossomak.flashcards.web.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// The website's only Haze import (Konsist enforces it).

/** A background that [blurBackdrop] components blur. */
@Stable
class BlurBackdrop internal constructor(internal val hazeState: HazeState)

/** The site's one backdrop, for components deep in a page that blur it. */
val LocalBlurBackdrop = staticCompositionLocalOf<BlurBackdrop> { error("No BlurBackdrop provided") }

@Composable
fun rememberBlurBackdrop(): BlurBackdrop {
    val hazeState = rememberHazeState()
    return remember(hazeState) { BlurBackdrop(hazeState) }
}

/** Marks the blurred background. It must be a sibling layer behind the content. */
fun Modifier.blurBackdropSource(backdrop: BlurBackdrop): Modifier = hazeSource(backdrop.hazeState)

/** Blurs [backdrop] behind this node by [radius], clipped to [shape]. */
fun Modifier.blurBackdrop(backdrop: BlurBackdrop, shape: Shape, radius: Dp): Modifier = clip(shape).hazeBlur(
    input = HazeInput.Sources(backdrop.hazeState),
    style = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(radius)
        noiseFactor(0f)
    },
)
