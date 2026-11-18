package com.rossomak.flashcards.web

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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

private const val ROUTE_CROSSFADE_MILLIS = 300

/** The site: the page for the URL path, crossfading over one shared backdrop whose graph morphs to each page. */
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
    // A page change morphs the graph from wherever it is.
    LaunchedEffect(route) {
        val morph: AnimationSpec<Float> = if (reducedMotion) snap() else tween(tuning.minMorphDuration.inWholeMilliseconds.toInt(), easing = FlashcardsMotion.StandardEasing)
        graphState.animateTo(route.graphSpec(tuning), morph)
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
                            active = route == Home,
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

/** The hero's graph on the landing page, the closing page's on the legal pages. */
private fun WebRoute.graphSpec(tuning: LandingTuning): MorpheusGraphSpec = if (this == Home) tuning.pages.first() else tuning.pages.last()
