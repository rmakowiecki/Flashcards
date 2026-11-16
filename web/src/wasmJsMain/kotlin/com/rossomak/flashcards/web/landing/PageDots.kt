package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.rossomak.flashcards.web.theme.WebDimensions

/** A column of one dot per page on the window's end edge: the current page's dot is larger and brighter, and a dot opens its page. */
@Composable
fun PageDots(modifier: Modifier = Modifier, pagerState: PagerState, onPageClick: (Int) -> Unit) {
    val currentPage = pagerState.currentPage
    Column(modifier = modifier.padding(end = WebDimensions.pageDotsEndPadding)) {
        repeat(pagerState.pageCount) { page ->
            val isCurrent = page == currentPage
            Box(
                modifier = Modifier
                    .size(WebDimensions.pageDotTouchTarget)
                    .clip(CircleShape)
                    .clickable(interactionSource = null, indication = null) { onPageClick(page) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics {
                        contentDescription = "Page ${page + 1} of ${pagerState.pageCount}"
                        selected = isCurrent
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) WebDimensions.pageDotCurrentSize else WebDimensions.pageDotSize)
                        .background(LocalContentColor.current.copy(alpha = if (isCurrent) 1f else IDLE_DOT_ALPHA), CircleShape),
                )
            }
        }
    }
}

private const val IDLE_DOT_ALPHA = 0.4f
