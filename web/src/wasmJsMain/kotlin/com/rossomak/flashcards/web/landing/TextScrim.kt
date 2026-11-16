package com.rossomak.flashcards.web.landing

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.web.theme.WebColors

/** How far the scrim fades out past the text's edges. */
private val scrimReach = 140.dp

/**
 * A soft, dark oval glow behind a block of text, fading out past its edges, so the text stays legible
 * over a dense graph without thinning the graph anywhere else. It sits in front of the text's own
 * [motion] layer rather than inside it, since a fading layer would clip the glow to the text's bounds,
 * so it follows the same [motion] itself.
 */
fun Modifier.textScrim(motion: TextMotion): Modifier = drawBehind {
    val alpha = motion.alpha()
    if (alpha <= 0f) return@drawBehind
    val reach = scrimReach.toPx()
    val radiusX = size.width / 2 + reach
    val radiusY = size.height / 2 + reach
    val center = Offset(size.width / 2, size.height / 2)
    translate(top = motion.translationYPx()) {
        scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to WebColors.textScrim,
                    SCRIM_SOLID_SHARE to WebColors.textScrim,
                    1f to Color.Transparent,
                    center = center,
                    radius = radiusX,
                ),
                radius = radiusX,
                center = center,
                alpha = alpha,
            )
        }
    }
}

/** Share of the scrim's radius held at full strength before it fades. */
private const val SCRIM_SOLID_SHARE = 0.35f
