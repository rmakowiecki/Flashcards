package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.LOGO_REVEAL_START_SCALE
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.components.reveal
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions

/** The gap above the closing links, as a share of the window's height: well below the text, yet clear of the bottom edge. */
private const val LINKS_TOP_SHARE = 0.16f

/** The last page: logo, tagline, legal links and contact address, revealed by [ClosingSlots]. */
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.reveal(progress = { reveal.progress(ClosingSlots.CALL_TO_ACTION) }, rise = rise),
                ) {
                    BalancedText(
                        text = "Stay sharp, study smart",
                        style = if (frame.layout == SideBySide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    BalancedText(
                        text = "Join Flashcards and start learning right now",
                        style = if (frame.layout == SideBySide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                        modifier = Modifier.padding(top = MaterialTheme.spacing.large * 2).widthIn(max = WebDimensions.heroDescriptionMaxWidth),
                    )
                }
                SiteLinks(
                    modifier = Modifier
                        .padding(top = (frame.height * LINKS_TOP_SHARE).coerceAtLeast(MaterialTheme.spacing.xlarge))
                        .reveal(progress = { reveal.progress(ClosingSlots.LINKS) }, rise = rise),
                    showContact = true,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}
