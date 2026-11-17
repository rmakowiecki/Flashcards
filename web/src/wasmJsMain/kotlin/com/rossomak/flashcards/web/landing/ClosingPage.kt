package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.LOGO_REVEAL_START_SCALE
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.components.reveal
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate

/**
 * The last page: the large logo ([HeroLogo]) over the tagline again, the legal links and the contact
 * address, as one block. The logo and the links reveal by [reveal]'s slots ([ClosingSlots]).
 */
@Composable
fun ClosingPage(modifier: Modifier = Modifier, frame: LandingFrame, textModifier: Modifier = Modifier, reveal: RevealSequence, onNavigate: OnNavigate) {
    val rise = LocalRevealMotion.current.rise
    Box(modifier = modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.medium)) {
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = frame.heroLogoTop).then(textModifier)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeroLogo(
                    frame = frame,
                    modifier = Modifier.reveal(progress = { reveal.progress(ClosingSlots.LOGO) }, rise = rise, startScale = LOGO_REVEAL_START_SCALE),
                )
                BalancedText(
                    text = "Stay sharp, study smart",
                    style = if (frame.layout == SideBySide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                SiteLinks(
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.xlarge)
                        .reveal(progress = { reveal.progress(ClosingSlots.LINKS) }, rise = rise),
                    showContact = true,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}
