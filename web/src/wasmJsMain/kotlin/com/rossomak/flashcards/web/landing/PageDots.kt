package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.web.components.BlurBackdrop
import com.rossomak.flashcards.web.components.blurBackdrop
import com.rossomak.flashcards.web.theme.WebColors
import com.rossomak.flashcards.web.theme.WebDimensions
import kotlin.math.abs

/** The page dots' sizes. [contentClearance] is how far a page's content stays from the window's end edge so the dots never cover it. */
class PageDotMetrics(
    val slot: Dp,
    val currentSize: Dp,
    val idleSize: Dp,
    val pillPadding: Dp,
    val endPadding: Dp,
    val contentClearance: Dp,
) {
    companion object {
        val Regular = PageDotMetrics(slot = 24.dp, currentSize = 10.dp, idleSize = 6.dp, pillPadding = 4.dp, endPadding = 8.dp, contentClearance = 40.dp)
        val Compact = PageDotMetrics(slot = 16.dp, currentSize = 6.dp, idleSize = 4.dp, pillPadding = 3.dp, endPadding = 4.dp, contentClearance = 32.dp)
    }
}

/** One dot per page on a frosted pill. A dot grows and brightens as the pages near it. Only an indicator: it takes no input. */
@Composable
fun PageDots(modifier: Modifier = Modifier, pagerState: PagerState, metrics: PageDotMetrics, backdrop: BlurBackdrop) {
    val pillShape = RoundedCornerShape(percent = PILL_CORNER_PERCENT)
    val dotColor = LocalContentColor.current
    Column(
        modifier = modifier
            .padding(end = metrics.endPadding)
            .blurBackdrop(backdrop = backdrop, shape = pillShape, radius = WebDimensions.pageDotsBlurRadius)
            .background(WebColors.frostedFill, pillShape)
            .border(width = Dp.Hairline, color = WebColors.frostedBorder, shape = pillShape)
            .padding(metrics.pillPadding)
            .clearAndSetSemantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${pagerState.pageCount}" },
    ) {
        repeat(pagerState.pageCount) { page ->
            Box(modifier = Modifier.size(metrics.slot), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(metrics.currentSize)
                        .graphicsLayer {
                            val nearness = PageDotHighlight.nearness(pagerState.currentPage + pagerState.currentPageOffsetFraction, page)
                            scaleX = lerp(metrics.idleSize / metrics.currentSize, 1f, nearness)
                            scaleY = scaleX
                            alpha = lerp(IDLE_DOT_ALPHA, 1f, nearness)
                        }
                        .background(dotColor, CircleShape),
                )
            }
        }
    }
}

object PageDotHighlight {
    /** 1 on [page], falling to 0 one page away. */
    fun nearness(position: Float, page: Int): Float = (1f - abs(position - page)).coerceIn(0f, 1f)
}

private const val IDLE_DOT_ALPHA = 0.4f
private const val PILL_CORNER_PERCENT = 50
