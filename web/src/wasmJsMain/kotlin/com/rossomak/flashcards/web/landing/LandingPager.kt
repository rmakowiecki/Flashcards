package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import com.rossomak.flashcards.web.browser.WheelTarget
import com.rossomak.flashcards.web.browser.WindowKeyHandler
import com.rossomak.flashcards.web.browser.WindowWheelHandler
import com.rossomak.flashcards.web.browser.isLocalPreview
import com.rossomak.flashcards.web.browser.rememberPrefersDarkColorScheme
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion
import com.rossomak.flashcards.web.components.BlurBackdrop
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.rememberRevealSequence
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.Follow
import com.rossomak.morpheus.MorpheusGraphMorphState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull

/** The hero, one page per [LandingFeature], and the closing page. */
val LANDING_PAGE_COUNT: Int = LandingFeature.entries.size + 2

/**
 * The landing page: snapping full-window pages, scrolled by the browser ([BrowserSnap]), driving
 * [graphState]'s morph. With [playEntrance], the hero reveals after the graph fades in. While not [active],
 * the page fades out under another route: it no longer takes keys or wheel, or moves the graph.
 */
@OptIn(ExperimentalMorpheusGraphApi::class)
@Composable
fun LandingPager(
    modifier: Modifier = Modifier,
    graphState: MorpheusGraphMorphState,
    tuning: LandingTuning,
    backdrop: BlurBackdrop,
    active: Boolean,
    playEntrance: Boolean,
    onEntrancePlayed: () -> Unit,
    onNavigate: OnNavigate,
) {
    val pagerState = rememberPagerState { LANDING_PAGE_COUNT }
    val reducedMotion by rememberPrefersReducedMotion()
    val prefersDark by rememberPrefersDarkColorScheme()
    val screenshotVariant = ScreenshotVariant.forColorScheme(prefersDark)
    val entrance = rememberEntrance(playEntrance = playEntrance, animate = !reducedMotion, onEntrancePlayed = onEntrancePlayed)

    val tunerOpen = remember { mutableStateOf(false) }
    FollowPagerWhileActive(graphState = graphState, tuning = tuning, pagerState = pagerState, active = active)

    val input = rememberPagerInput(pagerState, tunerOpen, active)
    input.reducedMotion = reducedMotion
    BrowserSnap(pagerState = pagerState, input = input, stopAtEveryPage = tuning.stopAtEveryPage)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frame = LandingFrame(width = maxWidth, height = maxHeight, sideBySideMinAspectRatio = tuning.sideBySideMinAspectRatio)
        // The landing page's text grows with the window, over the shared type scale.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * frame.fontScale)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // The browser scrolls the pages (BrowserSnap); the pager only follows it.
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> {
                        val reveal = rememberPageReveal(pagerState = pagerState, page = page, slotCount = HeroSlots.COUNT, gate = entrance.started)
                        // The scrim comes with the logo on first load, and stays while the logo replays on later arrivals.
                        val motion = TextMotion(
                            pagerState = pagerState,
                            page = page,
                            enabled = !reducedMotion,
                            scrimEntrance = { maxOf(reveal.progress(HeroSlots.LOGO), entrance.content.value) },
                        )
                        HeroPage(
                            frame = frame,
                            textModifier = Modifier.textScrim(motion).textMotion(motion),
                            reveal = reveal,
                            textEntrance = { entrance.content.value },
                            animateHint = !reducedMotion,
                            footerAlpha = { if (reducedMotion) 1f else LandingScale.heroFooterAlpha(pagerState.offsetOf(0)) },
                            onScrollHintClick = { input.step(PageStep.Next) },
                            onNavigate = onNavigate,
                        )
                    }
                    LANDING_PAGE_COUNT - 1 -> {
                        val reveal = rememberPageReveal(pagerState = pagerState, page = page, slotCount = ClosingSlots.COUNT, gate = true)
                        val motion = TextMotion(pagerState = pagerState, page = page, enabled = !reducedMotion)
                        ClosingPage(frame = frame, textModifier = Modifier.textScrim(motion).textMotion(motion), reveal = reveal, onNavigate = onNavigate)
                    }
                    else -> {
                        val motion = TextMotion(pagerState = pagerState, page = page, enabled = !reducedMotion)
                        FeaturePage(
                            frame = frame,
                            feature = LandingFeature.entries[page - 1],
                            screenshotVariant = screenshotVariant,
                            textModifier = Modifier.textScrim(motion).textMotion(motion),
                        )
                    }
                }
            }
        }
        PageDots(
            pagerState = pagerState,
            metrics = frame.dots,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = entrance.content.value },
        )
        // The logo and the links come last, and with the highest z, so nothing ever covers them.
        Box(modifier = Modifier.fillMaxSize().zIndex(HEADER_Z_INDEX)) {
            HeaderChrome(
                frame = frame,
                headerProgress = { headerProgressAt(pagerState, reducedMotion) },
                alpha = { entrance.content.value },
                onNavigate = onNavigate,
            )
        }
        if (tunerOpen.value) {
            LandingTunerPane(modifier = Modifier.align(Alignment.CenterEnd), tuning = tuning, pageIndex = pagerState.currentPage)
        }
    }
}

/** Morphs [graphState] with the pager. Under another route the legal page owns the graph, so a fading landing page leaves it alone. */
@OptIn(ExperimentalMorpheusGraphApi::class)
@Composable
private fun FollowPagerWhileActive(graphState: MorpheusGraphMorphState, tuning: LandingTuning, pagerState: PagerState, active: Boolean) {
    if (!active) return
    graphState.Follow(
        tuning.pages,
        minMorphDuration = tuning.minMorphDuration,
        target = { pagerState.targetPage },
    ) {
        pagerState.currentPage + pagerState.currentPageOffsetFraction
    }
}

/** How far the header has slid in at the pager's position. Under reduced motion it is in or out, with no slide or fade. */
private fun headerProgressAt(pagerState: PagerState, reducedMotion: Boolean): Float {
    val progress = LandingScale.headerProgress(pagerState.currentPage + pagerState.currentPageOffsetFraction, LANDING_PAGE_COUNT)
    return if (reducedMotion) progress.roundToInt().toFloat() else progress
}

/** The small logo at the top start and the legal links at the top end, sliding in together as [headerProgress] goes 0 to 1. */
@Composable
private fun BoxScope.HeaderChrome(frame: LandingFrame, headerProgress: () -> Float, alpha: () -> Float, onNavigate: OnNavigate) {
    HeaderLogo(frame = frame, headerProgress = headerProgress, alpha = alpha)
    HeaderLinks(modifier = Modifier.align(Alignment.TopEnd), frame = frame, headerProgress = headerProgress, alpha = alpha, onNavigate = onNavigate)
}

/** [page]'s reveal by [PageReveal], held back until [gate] opens. */
@Composable
private fun rememberPageReveal(pagerState: PagerState, page: Int, slotCount: Int, gate: Boolean): RevealSequence {
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState, page) {
        snapshotFlow { PageReveal.shown(isTarget = pagerState.targetPage == page, distancePages = abs(pagerState.offsetOf(page))) }
            .filterNotNull()
            .collect { shown -> arrived = shown }
    }
    return rememberRevealSequence(slotCount = slotCount, shown = arrived && gate)
}

/** How far [page] is from the position on screen, in pages: positive once scrolled past. */
fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/** A page's text lag and fade ([TextParallax]), off without [enabled]. [scrimEntrance] fades only the scrim. */
class TextMotion(private val pagerState: PagerState, private val page: Int, private val enabled: Boolean, private val scrimEntrance: () -> Float = { 1f }) {
    fun alpha(): Float = if (enabled) TextParallax.alpha(pagerState.offsetOf(page)) else 1f

    fun scrimAlpha(): Float = alpha() * scrimEntrance()

    fun translationYPx(): Float = if (enabled) TextParallax.translationFraction(pagerState.offsetOf(page)) * pagerState.layoutInfo.pageSize else 0f
}

/** Applies [motion] to the text. The scrim stays outside this layer so it is not clipped. */
private fun Modifier.textMotion(motion: TextMotion): Modifier = graphicsLayer {
    translationY = motion.translationYPx()
    alpha = motion.alpha()
}

/** First load: [started] after the graph fades in, then [content] reveals. */
private class Entrance(val content: Animatable<Float, *>, started: Boolean) {
    var started: Boolean by mutableStateOf(started)
}

/**
 * The first load's [Entrance]. Without [playEntrance], or without [animate], everything starts shown. The first
 * load counts as played even when not animated, so a later return to the page does not play it.
 */
@Composable
private fun rememberEntrance(playEntrance: Boolean, animate: Boolean, onEntrancePlayed: () -> Unit): Entrance {
    val motion by rememberUpdatedState(LocalRevealMotion.current)
    val entrance = remember { Entrance(content = Animatable(if (playEntrance && animate) 0f else 1f), started = !(playEntrance && animate)) }
    LaunchedEffect(Unit) {
        if (!playEntrance) return@LaunchedEffect
        onEntrancePlayed()
        if (!animate) return@LaunchedEffect
        delay(LandingTimings.entranceGraphFade)
        entrance.started = true
        delay(motion.stagger * HeroSlots.TEXT)
        entrance.content.animateTo(1f, motion.tween())
    }
    return entrance
}

/**
 * Window keys, and the wheel left to the browser except over the open tuner. T toggles [tunerOpen] locally.
 * Nothing is taken while not [active]: the keys and the wheel belong to the page now shown.
 */
@Composable
private fun rememberPagerInput(pagerState: PagerState, tunerOpen: MutableState<Boolean>, active: Boolean): LandingPagerInput {
    val isLocalPreview = remember { isLocalPreview() }
    val input = remember(pagerState) { LandingPagerInput(pageCount = pagerState.pageCount) }
    WindowWheelHandler(
        onBrowserWheel = input::onUserScroll,
        target = { clientX ->
            if (!active || (tunerOpen.value && clientX >= window.innerWidth - LANDING_TUNER_PANE_WIDTH.value)) WheelTarget.Compose else WheelTarget.Browser
        },
    )
    WindowKeyHandler(
        onKey = { key ->
            when {
                !active -> false
                isLocalPreview && key.equals(TUNER_KEY, ignoreCase = true) -> {
                    tunerOpen.value = !tunerOpen.value
                    true
                }
                else -> input.onKey(key)
            }
        },
    )
    return input
}

private const val TUNER_KEY = "t"

/** Above every page and phone. */
private const val HEADER_Z_INDEX = 10f
