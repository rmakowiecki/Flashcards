package com.rossomak.flashcards.web.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.web.components.BlurBackdrop
import com.rossomak.flashcards.web.landing.LandingPager
import com.rossomak.flashcards.web.landing.LandingTuning
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphMorphState

/** `/`: the landing page. [playEntrance] is true only on its first show. */
@OptIn(ExperimentalMorpheusGraphApi::class)
@Composable
fun HomePage(
    modifier: Modifier = Modifier,
    graphState: MorpheusGraphMorphState,
    tuning: LandingTuning,
    backdrop: BlurBackdrop,
    playEntrance: Boolean,
    onEntrancePlayed: () -> Unit,
    onNavigate: OnNavigate,
) {
    LandingPager(
        modifier = modifier,
        graphState = graphState,
        tuning = tuning,
        backdrop = backdrop,
        playEntrance = playEntrance,
        onEntrancePlayed = onEntrancePlayed,
        onNavigate = onNavigate,
    )
}
