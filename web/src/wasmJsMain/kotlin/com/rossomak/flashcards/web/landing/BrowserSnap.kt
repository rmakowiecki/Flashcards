package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.web.browser.BrowserSnapScroller

/**
 * While in composition, the browser scrolls and snaps the pages ([BrowserSnapScroller]) and the pager
 * follows its scroll position exactly; [input] scrolls it for keys and the page dots. With
 * [stopAtEveryPage], a swipe's snap stops at the next page. The pager takes no touch or wheel of its
 * own, so both reach the browser.
 */
@Composable
fun BrowserSnap(pagerState: PagerState, input: LandingPagerInput, stopAtEveryPage: Boolean) {
    var installed by remember { mutableStateOf<BrowserSnapScroller?>(null) }
    DisposableEffect(pagerState, input) {
        val scroller = BrowserSnapScroller(pageCount = pagerState.pageCount, onScroll = { positionPages -> pagerState.followTo(positionPages) })
        scroller.install(startPage = pagerState.currentPage)
        input.scroller = scroller
        installed = scroller
        onDispose {
            installed = null
            input.scroller = null
            scroller.uninstall()
        }
    }
    LaunchedEffect(installed, stopAtEveryPage) {
        installed?.setStopAtEveryPage(stopAtEveryPage)
    }
}

/** Moves the pages straight to [positionPages], in pages from the top. */
private fun PagerState.followTo(positionPages: Float) {
    val pageSizePx = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    if (pageSizePx <= 0f) return
    val deltaPx = (positionPages - (currentPage + currentPageOffsetFraction)) * pageSizePx
    if (deltaPx != 0f) dispatchRawDelta(deltaPx)
}
