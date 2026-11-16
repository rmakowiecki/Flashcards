package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.web.browser.WindowKeyHandler
import com.rossomak.flashcards.web.browser.WindowWheelHandler
import com.rossomak.flashcards.web.browser.rememberPrefersDarkColorScheme
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.morpheus.Follow
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState

/** The hero, one page per [LandingFeature], and the closing page. */
val LANDING_PAGE_COUNT: Int = LandingFeature.entries.size + 2

/**
 * The landing page: full-window pages that snap, over the brand gradient and one fixed graph that
 * morphs from page to page as they scroll. The wheel and touch scroll the pages freely and a snap
 * assist settles them on a page once they rest; keys and the page dots step page by page. With
 * [playEntrance], the graph fades in and then the hero fades up.
 */
@Composable
fun LandingPager(modifier: Modifier = Modifier, playEntrance: Boolean, onEntrancePlayed: () -> Unit, onNavigate: OnNavigate) {
    val pagerState = rememberPagerState { LANDING_PAGE_COUNT }
    val reducedMotion by rememberPrefersReducedMotion()
    val prefersDark by rememberPrefersDarkColorScheme()
    val screenshotVariant = ScreenshotVariant.forColorScheme(prefersDark)
    val entrance = rememberEntrance(playEntrance = playEntrance && !reducedMotion, onEntrancePlayed = onEntrancePlayed)

    val graphState = rememberMorpheusGraphMorphState(LandingGraphs.pages.first())
    graphState.Follow(
        LandingGraphs.pages,
        minMorphDuration = LandingTimings.minMorphDuration,
        target = { pagerState.targetPage },
    ) {
        pagerState.currentPage + pagerState.currentPageOffsetFraction
    }

    val input = rememberPagerInput(pagerState)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.brandColors.screenGradient)) {
        val frame = LandingFrame(width = maxWidth, height = maxHeight)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = entrance.graph.value }
                .morpheusGraphBackground(graphState),
        )
        // The landing page's text grows with the window, over the shared type scale.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * frame.fontScale)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // A fling travels as far as its speed carries it, as on any page, then settles on a page.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(LANDING_PAGE_COUNT),
                    snapAnimationSpec = LandingTimings.snapAnimationSpec(),
                ),
            ) { page ->
                val textMotion = TextMotion(pagerState = pagerState, page = page, enabled = !reducedMotion).let { motion -> Modifier.textScrim(motion).textMotion(motion) }
                when (page) {
                    0 -> HeroPage(
                        frame = frame,
                        textModifier = textMotion,
                        entranceProgress = { entrance.content.value },
                        animateHint = !reducedMotion,
                        onScrollHintClick = { input.step(PageStep.Next) },
                        onNavigate = onNavigate,
                    )
                    LANDING_PAGE_COUNT - 1 -> ClosingPage(frame = frame, textModifier = textMotion, onNavigate = onNavigate)
                    else -> FeaturePage(
                        frame = frame,
                        feature = LandingFeature.entries[page - 1],
                        screenshotVariant = screenshotVariant,
                        textModifier = textMotion,
                    )
                }
            }
        }
        LandingLogo(
            frame = frame,
            headerProgress = { LandingScale.headerProgress(pagerState.currentPage + pagerState.currentPageOffsetFraction, LANDING_PAGE_COUNT) },
            alpha = { entrance.content.value },
            onClick = { input.goTo(0) },
        )
        PageDots(
            pagerState = pagerState,
            onPageClick = input::goTo,
            modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = entrance.content.value },
        )
    }
}

/** How far [page] is from the position on screen, in pages: positive once scrolled past. */
fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/**
 * A page's text lags slightly behind its page and fades as it leaves; without [enabled] (reduced
 * motion) it moves with its page and never fades.
 */
class TextMotion(private val pagerState: PagerState, private val page: Int, private val enabled: Boolean) {
    fun alpha(): Float = if (enabled) TextParallax.alpha(pagerState.offsetOf(page)) else 1f

    fun translationYPx(): Float = if (enabled) TextParallax.translationFraction(pagerState.offsetOf(page)) * pagerState.layoutInfo.pageSize else 0f
}

/** Applies [motion] to the text. The text's scrim sits outside this layer ([textScrim]), so the layer never clips it. */
private fun Modifier.textMotion(motion: TextMotion): Modifier = graphicsLayer {
    translationY = motion.translationYPx()
    alpha = motion.alpha()
}

private class Entrance(val graph: Animatable<Float, *>, val content: Animatable<Float, *>)

/** First load only: the graph fades in, then the hero fades up. Without [playEntrance] both start shown. */
@Composable
private fun rememberEntrance(playEntrance: Boolean, onEntrancePlayed: () -> Unit): Entrance {
    val entrance = remember {
        val start = if (playEntrance) 0f else 1f
        Entrance(graph = Animatable(start), content = Animatable(start))
    }
    LaunchedEffect(Unit) {
        if (!playEntrance) return@LaunchedEffect
        onEntrancePlayed()
        entrance.graph.animateTo(1f, tween(LandingTimings.entranceGraphFade.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.StandardEasing))
        entrance.content.animateTo(1f, tween(LandingTimings.entranceContentFade.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.EmphasizedDecelerateEasing))
    }
    return entrance
}

/** Mouse-wheel notches, trackpad glides and keys, read from the window while the landing page shows. */
@Composable
private fun rememberPagerInput(pagerState: PagerState): LandingPagerInput {
    val scope = rememberCoroutineScope()
    val input = remember(pagerState, scope) { LandingPagerInput(pagerState, scope) }
    WindowWheelHandler(onWheel = input::onWheel)
    WindowKeyHandler(onKey = input::onKey)
    return input
}
