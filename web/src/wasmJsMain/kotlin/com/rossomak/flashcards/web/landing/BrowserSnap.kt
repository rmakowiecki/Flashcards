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

/** Installs a [BrowserSnapScroller] while in composition. The pager follows its scroll exactly. */
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

private fun PagerState.followTo(positionPages: Float) {
    val pageSizePx = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    if (pageSizePx <= 0f) return
    val deltaPx = (positionPages - (currentPage + currentPageOffsetFraction)) * pageSizePx
    if (deltaPx != 0f) dispatchRawDelta(deltaPx)
}
