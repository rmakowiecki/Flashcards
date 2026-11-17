package com.rossomak.flashcards.web

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.web.browser.rememberPrefersReducedMotion
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.SiteBackdrop
import com.rossomak.flashcards.web.components.rememberBlurBackdrop
import com.rossomak.flashcards.web.landing.LandingTimings
import com.rossomak.flashcards.web.landing.LandingTuning
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.navigation.WebRoute.DeleteAccount
import com.rossomak.flashcards.web.navigation.WebRoute.Home
import com.rossomak.flashcards.web.navigation.WebRoute.PrivacyPolicy
import com.rossomak.flashcards.web.navigation.rememberBrowserNavigator
import com.rossomak.flashcards.web.pages.DeleteAccountPage
import com.rossomak.flashcards.web.pages.HomePage
import com.rossomak.flashcards.web.pages.PrivacyPolicyPage
import com.rossomak.flashcards.web.theme.WebTheme
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import kotlinx.browser.document

/** How long the old page takes to fade out as the new one fades in. */
private const val ROUTE_CROSSFADE_MILLIS = 300

/**
 * The whole site: one page at a time, chosen by the browser's URL path, crossfading from one to the
 * next over one shared backdrop. The graph morphs to each page's shape, the landing page's hero or the
 * legal pages' calm one, and fades in on first load when the site opens on the landing page. The
 * landing page's entrance plays once, on first load.
 */
@OptIn(ExperimentalMorpheusGraphApi::class)
@Composable
fun WebApp() {
    val navigator = rememberBrowserNavigator()
    val route = navigator.route
    var homeEntrancePending by remember { mutableStateOf(route == Home) }
    val reducedMotion by rememberPrefersReducedMotion()

    val tuning = remember { LandingTuning() }
    val graphState = rememberMorpheusGraphMorphState(route.graphSpec(tuning))
    val graphAlpha = remember { Animatable(if (route == Home && !reducedMotion) 0f else 1f) }
    val backdrop = rememberBlurBackdrop()

    LaunchedEffect(route) { document.title = route.title }
    LaunchedEffect(Unit) {
        graphAlpha.animateTo(1f, tween(LandingTimings.entranceGraphFade.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.StandardEasing))
    }
    // A page change morphs the graph from wherever it is; the landing page then follows its scroll from the hero.
    LaunchedEffect(route) {
        graphState.animateTo(route.graphSpec(tuning), tween(tuning.minMorphDuration.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.StandardEasing))
    }

    WebTheme {
        CompositionLocalProvider(LocalRevealMotion provides tuning.reveal) {
            Box(modifier = Modifier.fillMaxSize()) {
                SiteBackdrop(backdrop = backdrop, graphState = graphState, graphAlpha = { graphAlpha.value }, showViolations = tuning.showViolations)
                Crossfade(targetState = route, animationSpec = if (reducedMotion) snap() else tween(ROUTE_CROSSFADE_MILLIS), label = "route") { shown ->
                    when (shown) {
                        Home -> HomePage(
                            graphState = graphState,
                            tuning = tuning,
                            backdrop = backdrop,
                            playEntrance = homeEntrancePending,
                            onEntrancePlayed = { homeEntrancePending = false },
                            onNavigate = navigator::navigate,
                        )
                        PrivacyPolicy -> PrivacyPolicyPage(onNavigate = navigator::navigate)
                        DeleteAccount -> DeleteAccountPage(onNavigate = navigator::navigate)
                    }
                }
            }
        }
    }
}

/** The graph a page opens on: the hero's on the landing page, which always opens on the hero, and the closing page's calm one on the legal pages. */
private fun WebRoute.graphSpec(tuning: LandingTuning): MorpheusGraphSpec = if (this == Home) tuning.pages.first() else tuning.pages.last()
