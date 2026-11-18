package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMotion
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.HoverBrightens
import com.rossomak.flashcards.web.components.LOGO_REVEAL_START_SCALE
import com.rossomak.flashcards.web.components.LocalRevealMotion
import com.rossomak.flashcards.web.components.RevealSequence
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.components.reveal
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions

/** The first page: logo, tagline and description, a scroll hint, and legal links. [textEntrance] reveals the text on first load. */
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
    val isWide = frame.layout == SideBySide
    val rise = LocalRevealMotion.current.rise
    Box(modifier = modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.medium)) {
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = frame.heroLogoTop).then(textModifier)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeroLogo(
                    frame = frame,
                    modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.LOGO) }, rise = rise, startScale = LOGO_REVEAL_START_SCALE),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.reveal(progress = textEntrance, rise = rise)) {
                    BalancedText(
                        text = "Stay sharp, study smart",
                        style = if (isWide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    BalancedText(
                        text = "Sharpen your dev skills: curated flashcards for Kotlin, Android, Python and more",
                        style = if (isWide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                        modifier = Modifier.padding(top = MaterialTheme.spacing.large * 2).widthIn(max = WebDimensions.heroDescriptionMaxWidth),
                    )
                }
            }
        }
        Column(
            // One glow behind the hint and links, arriving with the hint. The scroll fades the hint, links and glow together.
            // The glow follows the scroll itself: a layer alpha around it would clip it to the block's bounds.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .textScrim(reach = WebDimensions.bottomLinksScrimReach, alpha = { reveal.progress(HeroSlots.HINT) * footerAlpha() })
                .graphicsLayer { alpha = footerAlpha() }
                .padding(bottom = MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            ScrollHint(
                modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.HINT) }, rise = rise),
                animate = animateHint,
                onClick = onScrollHintClick,
            )
            SiteLinks(
                modifier = Modifier.reveal(progress = { reveal.progress(HeroSlots.LINKS) }, rise = rise),
                showContact = false,
                onNavigate = onNavigate,
            )
        }
    }
}

/** "Scroll to explore" over a bobbing chevron, muted until hovered. */
@Composable
private fun ScrollHint(modifier: Modifier = Modifier, animate: Boolean, onClick: () -> Unit) {
    val bob = if (animate) rememberHintBob() else remember { mutableFloatStateOf(0f) }
    HoverBrightens(modifier = modifier, restAlpha = WEB_MUTED_CONTENT_ALPHA) {
        Column(
            modifier = Modifier
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

private const val CHEVRON_ARMS = 0.3f
private const val CHEVRON_TIP = 0.7f
private const val HINT_BOB_MS = 900
