package com.rossomak.flashcards.web.landing

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.web.theme.WebColors

/** How far the scrim fades out past the text's edges. */
private val scrimReach = 320.dp

/**
 * The scrim's strength along its radius, from the center out: full over the text's middle, then a long
 * ease-out so it has no visible edge.
 */
private val scrimFalloff: Array<Pair<Float, Color>> = arrayOf(
    0f to WebColors.textScrim,
    0.2f to WebColors.textScrim,
    0.4f to WebColors.textScrim.scaledAlpha(0.82f),
    0.55f to WebColors.textScrim.scaledAlpha(0.58f),
    0.7f to WebColors.textScrim.scaledAlpha(0.34f),
    0.85f to WebColors.textScrim.scaledAlpha(0.13f),
    1f to Color.Transparent,
)

/**
 * A soft, dark oval glow behind a block of text, fading out well past its edges, so the text stays legible
 * over a dense graph without thinning the graph anywhere else. It sits in front of the text's own
 * [motion] layer rather than inside it, since a fading layer would clip the glow to the text's bounds,
 * so it follows the same [motion] itself.
 */
fun Modifier.textScrim(motion: TextMotion): Modifier = textScrim(reach = scrimReach, alpha = motion::scrimAlpha, translationYPx = motion::translationYPx)

/** The same glow behind a block that moves with its page, such as the hero's links: [reach] past its edges. */
fun Modifier.textScrim(reach: Dp, alpha: () -> Float = { 1f }, translationYPx: () -> Float = { 0f }): Modifier = drawBehind {
    val alpha = alpha()
    if (alpha <= 0f) return@drawBehind
    val reachPx = reach.toPx()
    val radiusX = size.width / 2 + reachPx
    val radiusY = size.height / 2 + reachPx
    val center = Offset(size.width / 2, size.height / 2)
    translate(top = translationYPx()) {
        scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(colorStops = scrimFalloff, center = center, radius = radiusX),
                radius = radiusX,
                center = center,
                alpha = alpha,
            )
        }
    }
}

private fun Color.scaledAlpha(share: Float): Color = copy(alpha = alpha * share)
