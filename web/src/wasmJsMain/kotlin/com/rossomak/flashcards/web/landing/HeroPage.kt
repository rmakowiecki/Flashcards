package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.HoverBrightens
import com.rossomak.flashcards.web.components.LOGO_REVEAL_START_SCALE
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.ProjectButtons
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.components.focusRing
import com.rossomak.flashcards.web.components.reveal
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions

/** The first page: logo, tagline and description, the GitHub and Google Play buttons, a scroll hint, and legal links. [textEntrance] reveals the text on first load. */
@Composable
fun HeroPage(
    modifier: Modifier = Modifier,
    frame: LandingFrame,
    textModifier: Modifier = Modifier,
    reveal: RevealSequence,
    textEntrance: () -> Float,
    animateHint: Boolean,
    footerAlpha: () -> Float,
    onScrollHintClick: () -> Unit,
    onNavigate: OnNavigate,
) {
    val rise = LocalRevealMotion.current.rise
    val textStyles = heroTextStyles(frame)
    // Once faded out the footer is left out, so its invisible hint and links take no click or focus.
    val footerShown by remember { derivedStateOf { footerAlpha() > 0f } }
    HeroStack(
        modifier = modifier.fillMaxSize().padding(horizontal = heroSideInset(frame)),
        frame = frame,
        textModifier = textModifier,
        logo = {
            HeroLogo(
                frame = frame,
                modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.LOGO) }, rise = rise, startScale = LOGO_REVEAL_START_SCALE),
            )
        },
        body = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.reveal(progress = textEntrance, rise = rise)) {
                    BalancedText(
                        text = "Stay sharp, study smart",
                        style = textStyles.tagline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    BalancedText(
                        text = "Sharpen your dev skills: curated flashcards for Kotlin, Android, Python and more",
                        style = textStyles.description,
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                        modifier = Modifier.padding(top = textStyles.gap).widthIn(max = WebDimensions.heroDescriptionMaxWidth),
                    )
                }
                ProjectButtons(
                    modifier = Modifier
                        .padding(top = projectButtonsGap(frame))
                        .reveal(progress = { reveal.progress(HeroSlots.BUTTONS) }, rise = rise),
                    compact = frame.isCompact,
                )
            }
        },
        footer = {
            if (footerShown) {
                Column(
                    // One glow behind the hint and links, arriving with the hint. The scroll fades the hint, links and glow together.
                    // The glow follows the scroll itself: a layer alpha around it would clip it to the block's bounds.
                    modifier = Modifier
                        .textScrim(reach = WebDimensions.bottomLinksScrimReach, alpha = { reveal.progress(HeroSlots.HINT) * footerAlpha() })
                        .graphicsLayer { alpha = footerAlpha() }
                        .padding(bottom = MaterialTheme.spacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    // A short window has no room for the hint, and swiping needs none.
                    if (!frame.isCompactHeight) {
                        ScrollHint(
                            modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.HINT) }, rise = rise),
                            animate = animateHint,
                            onClick = onScrollHintClick,
                        )
                    }
                    SiteLinks(
                        modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.LINKS) }, rise = rise),
                        showContact = false,
                        onNavigate = onNavigate,
                    )
                }
            }
        },
    )
}

/** The tagline, its description and the gap between them on the hero and the closing page. */
class HeroTextStyles(val tagline: TextStyle, val description: TextStyle, val gap: Dp)

@Composable
fun heroTextStyles(frame: LandingFrame): HeroTextStyles = when {
    // A short window keeps its room for the logo.
    frame.isCompactHeight || frame.height < SHORT_HERO_MAX_HEIGHT -> HeroTextStyles(
        tagline = MaterialTheme.typography.headlineSmall,
        description = MaterialTheme.typography.titleMedium,
        gap = MaterialTheme.spacing.medium,
    )
    frame.layout == SideBySide -> HeroTextStyles(
        tagline = MaterialTheme.typography.displaySmall,
        description = MaterialTheme.typography.headlineSmall,
        gap = MaterialTheme.spacing.large * 2,
    )
    else -> HeroTextStyles(
        tagline = MaterialTheme.typography.headlineMedium,
        description = MaterialTheme.typography.titleMedium,
        gap = MaterialTheme.spacing.large * 2,
    )
}

/** The gap above the GitHub and Google Play buttons on the hero and the closing page. */
@Composable
fun projectButtonsGap(frame: LandingFrame): Dp = if (frame.isCompactHeight) MaterialTheme.spacing.normal else MaterialTheme.spacing.large

/** The hero's and closing page's side inset: a compact window keeps the text clear of the page dots. */
@Composable
fun heroSideInset(frame: LandingFrame): Dp = if (frame.isCompact) frame.dots.contentClearance else MaterialTheme.spacing.medium

/** "Scroll to explore" over a bobbing chevron, muted until hovered. */
@Composable
private fun ScrollHint(modifier: Modifier = Modifier, animate: Boolean, onClick: () -> Unit) {
    val bob = if (animate) rememberHintBob() else remember { mutableFloatStateOf(0f) }
    HoverBrightens(modifier = modifier, restAlpha = WEB_MUTED_CONTENT_ALPHA) {
        Column(
            modifier = Modifier
                .focusRing(RoundedCornerShape(WebDimensions.focusRingCornerRadius))
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(MaterialTheme.spacing.xsmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Scroll to explore", style = MaterialTheme.typography.titleMedium)
            val chevronColor = LocalContentColor.current
            Canvas(
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.xxsmall)
                    .size(WebDimensions.scrollHintChevronSize)
                    .graphicsLayer { translationY = bob.value * WebDimensions.scrollHintBob.toPx() },
            ) {
                val strokeWidth = 2.dp.toPx()
                val middle = Offset(size.width / 2, size.height * CHEVRON_TIP)
                drawLine(chevronColor, Offset(0f + strokeWidth, size.height * CHEVRON_ARMS), middle, strokeWidth, StrokeCap.Round)
                drawLine(chevronColor, Offset(size.width - strokeWidth, size.height * CHEVRON_ARMS), middle, strokeWidth, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun rememberHintBob(): State<Float> = rememberInfiniteTransition(label = "scroll hint").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(HINT_BOB_MS, easing = FlashcardsMotion.StandardEasing), RepeatMode.Reverse),
    label = "scroll hint bob",
)

/** Windows shorter than this set the hero's and closing page's text small, as a phone in landscape does. */
private val SHORT_HERO_MAX_HEIGHT = 700.dp

private const val CHEVRON_ARMS = 0.3f
private const val CHEVRON_TIP = 0.7f
private const val HINT_BOB_MS = 900
