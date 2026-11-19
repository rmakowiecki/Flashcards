package com.rossomak.flashcards.web.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.rossomak.flashcards.web.theme.WebColors
import com.rossomak.flashcards.web.theme.WebDimensions

/** A ring in the shape of [shape] while the focusable below it, such as a `clickable`, has focus, so a keyboard user sees where they are. */
@Composable
fun Modifier.focusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { state -> focused = state.isFocused }
        .then(if (focused) Modifier.border(WebDimensions.focusRingWidth, WebColors.focusRing, shape) else Modifier)
}

/**
 * The [focusRing] for a control filled in the ring's own color: the ring is drawn a small gap outside [shape], so the
 * fill never hides it. It draws past the control's bounds and takes no layout space.
 */
@Composable
fun Modifier.outsetFocusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { state -> focused = state.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val strokeWidth = WebDimensions.focusRingWidth.toPx()
                val outset = WebDimensions.focusRingGap.toPx() + strokeWidth / 2
                val outline = shape.createOutline(Size(size.width + outset * 2, size.height + outset * 2), layoutDirection, this)
                translate(left = -outset, top = -outset) {
                    drawOutline(outline = outline, color = WebColors.focusRing, style = Stroke(width = strokeWidth))
                }
            }
        }
}
