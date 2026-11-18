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

/** Installs a [BrowserSnapScroller] while in composition and [active]. The pager follows its scroll exactly. */
@Composable
fun BrowserSnap(pagerState: PagerState, input: LandingPagerInput, stopAtEveryPage: Boolean, active: Boolean) {
    var installed by remember { mutableStateOf<BrowserSnapScroller?>(null) }
    DisposableEffect(pagerState, input, active) {
        if (!active) return@DisposableEffect onDispose {}
        val scroller = BrowserSnapScroller(
            pageCount = pagerState.pageCount,
            onScroll = { positionPages ->
                input.onPosition(positionPages)
                pagerState.followTo(positionPages)
            },
            onUserScroll = input::onUserScroll,
        )
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
