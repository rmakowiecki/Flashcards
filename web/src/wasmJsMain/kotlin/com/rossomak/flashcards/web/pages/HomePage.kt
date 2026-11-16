package com.rossomak.flashcards.web.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.web.landing.LandingPager
import com.rossomak.flashcards.web.navigation.OnNavigate

/** `/`: the landing page. [playEntrance] is true only the first time it shows after the site loads. */
@Composable
fun HomePage(modifier: Modifier = Modifier, playEntrance: Boolean, onEntrancePlayed: () -> Unit, onNavigate: OnNavigate) {
    LandingPager(modifier = modifier, playEntrance = playEntrance, onEntrancePlayed = onEntrancePlayed, onNavigate = onNavigate)
}
