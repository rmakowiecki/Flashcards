package com.rossomak.flashcards.web.landing

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints

/**
 * A paragraph wrapped so its lines are about equally wide, as CSS `text-wrap: balance` does, which a
 * canvas-drawn page cannot use: it keeps the line count it has at the full width it is given, but lays
 * out at the narrowest width that still fits that many lines ([balancedLineWidth]). It is never wider
 * than the width it is given, and sits inside it by its parent's alignment.
 */
@Composable
fun BalancedText(text: String, style: TextStyle, textAlign: TextAlign, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer()
    val mergedStyle = LocalTextStyle.current.merge(style)
    // The balanced width for the last width given, so a relayout at the same width measures nothing.
    val lastBalance = remember(text, mergedStyle) { IntArray(2) { NOT_MEASURED } }
    Text(
        text = text,
        style = style,
        textAlign = textAlign,
        color = color,
        modifier = modifier.layout { measurable, constraints ->
            val width = if (!constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else if (lastBalance[GIVEN] == constraints.maxWidth) {
                lastBalance[BALANCED]
            } else {
                balancedLineWidth(maxWidth = constraints.maxWidth) { lineWidth ->
                    measurer.measure(text = text, style = mergedStyle, constraints = Constraints(maxWidth = lineWidth)).lineCount
                }.also { balanced ->
                    lastBalance[GIVEN] = constraints.maxWidth
                    lastBalance[BALANCED] = balanced
                }
            }
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    )
}

/**
 * The narrowest width, in pixels up to [maxWidth], at which a paragraph breaks into no more lines than at
 * [maxWidth], found by halving the range; [lineCountAt] gives the paragraph's line count at a width. A
 * paragraph on one line keeps [maxWidth], since it has nothing to balance.
 */
fun balancedLineWidth(maxWidth: Int, lineCountAt: (width: Int) -> Int): Int {
    val lineCount = lineCountAt(maxWidth)
    if (lineCount <= 1) return maxWidth
    var tooNarrow = maxWidth / lineCount - 1
    var wideEnough = maxWidth
    while (wideEnough - tooNarrow > PRECISION_PX) {
        val middle = (tooNarrow + wideEnough) / 2
        if (lineCountAt(middle) <= lineCount) wideEnough = middle else tooNarrow = middle
    }
    return wideEnough
}

/** The balanced width is found to within this many pixels. */
private const val PRECISION_PX = 1
private const val NOT_MEASURED = -1
private const val GIVEN = 0
private const val BALANCED = 1
