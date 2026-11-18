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

private val scrimReach = 320.dp

/** Full in the middle, then a long ease-out with no visible edge. */
private val scrimFalloff: Array<Pair<Float, Color>> = arrayOf(
    0f to WebColors.textScrim,
    0.2f to WebColors.textScrim,
    0.4f to WebColors.textScrim.scaledAlpha(0.82f),
    0.55f to WebColors.textScrim.scaledAlpha(0.58f),
    0.7f to WebColors.textScrim.scaledAlpha(0.34f),
    0.85f to WebColors.textScrim.scaledAlpha(0.13f),
    1f to Color.Transparent,
)

/** A soft dark oval glow behind text. It sits outside the text's layer, which would clip it, and follows [motion] itself. */
fun Modifier.textScrim(motion: TextMotion): Modifier = textScrim(reach = scrimReach, alpha = motion::scrimAlpha, translationYPx = motion::translationYPx)

/** The same glow, reaching [reach] past the block's edges. */
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
