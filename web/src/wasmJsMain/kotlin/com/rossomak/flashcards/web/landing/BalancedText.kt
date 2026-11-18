package com.rossomak.flashcards.web.landing

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints

/** Text wrapped like CSS `text-wrap: balance`: laid out at [balancedLineWidth] of the width given. */
@Composable
fun BalancedText(text: String, style: TextStyle, textAlign: TextAlign, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer()
    val mergedStyle = LocalTextStyle.current.merge(style)
    // Caches the last given and balanced widths. Font sizes are in sp, so a new density or font scale needs a new balance.
    val density = LocalDensity.current
    val lastBalance = remember(text, mergedStyle, density.density, density.fontScale) { IntArray(2) { NOT_MEASURED } }
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
 * The narrowest width that keeps the line count at [maxWidth], by bisection. One line keeps [maxWidth]. Explicit
 * line breaks can make the line count fit at far less than a line count's share of [maxWidth], so the search
 * starts below every width.
 */
fun balancedLineWidth(maxWidth: Int, lineCountAt: (width: Int) -> Int): Int {
    val lineCount = lineCountAt(maxWidth)
    if (lineCount <= 1) return maxWidth
    var tooNarrow = 0
    var wideEnough = maxWidth
    while (wideEnough - tooNarrow > PRECISION_PX) {
        val middle = (tooNarrow + wideEnough) / 2
        if (lineCountAt(middle) <= lineCount) wideEnough = middle else tooNarrow = middle
    }
    return wideEnough
}

private const val PRECISION_PX = 1
private const val NOT_MEASURED = -1
private const val GIVEN = 0
private const val BALANCED = 1
