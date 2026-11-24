package com.rossomak.flashcards.web.landing

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
import com.rossomak.flashcards.web.components.HoverBrightens
import com.rossomak.flashcards.web.components.LOGO_REVEAL_START_SCALE
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.NewTabLink
import com.rossomak.flashcards.web.components.ProjectButtons
import com.rossomak.flashcards.web.components.ProjectLinks
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.components.reveal
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions

/** The gap above the closing links, as a share of the window's height: below the buttons and their hint. */
private const val LINKS_TOP_SHARE = 0.04f

/** The last page: logo, tagline, the GitHub and Google Play buttons, legal links, contact address and byline, revealed by [ClosingSlots]. */
@Composable
fun ClosingPage(modifier: Modifier = Modifier, frame: LandingFrame, textModifier: Modifier = Modifier, reveal: RevealSequence, onNavigate: OnNavigate) {
    val rise = LocalRevealMotion.current.rise
    val textStyles = heroTextStyles(frame)
    // A short window has no room for the full gap: the links stay in view.
    val linksMinGap = if (frame.isCompactHeight) MaterialTheme.spacing.normal else MaterialTheme.spacing.medium
    HeroStack(
        modifier = modifier.fillMaxSize().padding(horizontal = heroSideInset(frame)),
        frame = frame,
        textModifier = textModifier,
        logo = {
            HeroLogo(
                frame = frame,
                modifier = Modifier.reveal(progress = { reveal.progress(ClosingSlots.LOGO) }, rise = rise, startScale = LOGO_REVEAL_START_SCALE),
            )
        },
        body = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.reveal(progress = { reveal.progress(ClosingSlots.CALL_TO_ACTION) }, rise = rise),
                ) {
                    BalancedText(
                        text = "Stay sharp, study smart",
                        style = textStyles.tagline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    BalancedText(
                        text = "Coming soon to Google Play. Follow along on GitHub.",
                        style = textStyles.description,
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                        modifier = Modifier.padding(top = textStyles.gap).widthIn(max = WebDimensions.heroDescriptionMaxWidth),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.reveal(progress = { reveal.progress(ClosingSlots.LINKS) }, rise = rise),
                ) {
                    ProjectButtons(
                        modifier = Modifier.padding(top = projectButtonsGap(frame)),
                        compact = frame.isCompact,
                    )
                    SiteLinks(
                        modifier = Modifier.padding(top = (frame.height * LINKS_TOP_SHARE).coerceAtLeast(linksMinGap)),
                        showContact = true,
                        onNavigate = onNavigate,
                    )
                    HoverBrightens(modifier = Modifier.padding(top = MaterialTheme.spacing.xsmall), restAlpha = WEB_MUTED_CONTENT_ALPHA) {
                        NewTabLink(
                            prefix = "Built by ",
                            label = ProjectLinks.AUTHOR_NAME,
                            url = ProjectLinks.AUTHOR_URL,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
    )
}
