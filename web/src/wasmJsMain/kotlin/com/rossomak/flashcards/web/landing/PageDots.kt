package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.web.components.BlurBackdrop
import com.rossomak.flashcards.web.components.blurBackdrop
import com.rossomak.flashcards.web.components.hoverProgress
import com.rossomak.flashcards.web.theme.WebColors
import com.rossomak.flashcards.web.theme.WebDimensions
import kotlin.math.abs

/** One dot per page on a frosted pill. A dot grows and brightens as the pages near it, and opens its page. */
@Composable
fun PageDots(modifier: Modifier = Modifier, pagerState: PagerState, backdrop: BlurBackdrop, onPageClick: (Int) -> Unit) {
    val currentPage = pagerState.currentPage
    val pillShape = RoundedCornerShape(percent = PILL_CORNER_PERCENT)
    val dotColor = LocalContentColor.current
    Column(
        modifier = modifier
            .padding(end = WebDimensions.pageDotsEndPadding)
            .blurBackdrop(backdrop = backdrop, shape = pillShape, radius = WebDimensions.pageDotsBlurRadius)
            .background(WebColors.frostedFill, pillShape)
            .border(width = Dp.Hairline, color = WebColors.frostedBorder, shape = pillShape)
            .padding(WebDimensions.pageDotsPillPadding),
    ) {
        repeat(pagerState.pageCount) { page ->
            val interactionSource = remember { MutableInteractionSource() }
            val hover by interactionSource.hoverProgress()
            Box(
                modifier = Modifier
                    .size(WebDimensions.pageDotTouchTarget)
                    .clip(CircleShape)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = null, indication = null) { onPageClick(page) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics {
                        contentDescription = "Page ${page + 1} of ${pagerState.pageCount}"
                        selected = page == currentPage
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(WebDimensions.pageDotCurrentSize)
                        .graphicsLayer {
                            val nearness = PageDotHighlight.nearness(pagerState.currentPage + pagerState.currentPageOffsetFraction, page)
                            val idleScale = WebDimensions.pageDotSize / WebDimensions.pageDotCurrentSize
                            scaleX = lerp(idleScale, 1f, nearness) * (1f + HOVER_GROWTH * hover)
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

private const val HOVER_GROWTH = 0.3f
