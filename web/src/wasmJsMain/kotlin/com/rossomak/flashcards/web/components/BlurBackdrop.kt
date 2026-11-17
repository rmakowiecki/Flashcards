package com.rossomak.flashcards.web.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
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

// The website's only file allowed to import `dev.chrisbanes.haze` (Konsist enforces it), the
// counterpart of `:core:ui`'s BlurBackdrop.kt, which the Android-only module keeps from the site.

/** A background that [blurBackdrop] components blur; mark it with [blurBackdropSource]. */
@Stable
class BlurBackdrop internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberBlurBackdrop(): BlurBackdrop {
    val hazeState = rememberHazeState()
    return remember(hazeState) { BlurBackdrop(hazeState) }
}

/**
 * Marks a background that [blurBackdrop] components blur. It records its children too and a
 * component cannot blur itself, so it must be a sibling layer behind the content.
 */
fun Modifier.blurBackdropSource(backdrop: BlurBackdrop): Modifier = hazeSource(backdrop.hazeState)

/** Blurs [backdrop] behind this node by [radius], clipped to [shape]; the component's own container is drawn over it. */
fun Modifier.blurBackdrop(backdrop: BlurBackdrop, shape: Shape, radius: Dp): Modifier = clip(shape).hazeBlur(
    input = HazeInput.Sources(backdrop.hazeState),
    style = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(radius)
        noiseFactor(0f)
    },
)
