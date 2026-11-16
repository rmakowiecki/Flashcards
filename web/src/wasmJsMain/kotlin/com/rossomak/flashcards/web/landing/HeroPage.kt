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
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA
import com.rossomak.flashcards.web.theme.WebDimensions

/**
 * The first page: the tagline and a one-line description under the large logo (drawn by
 * [LandingLogo], which stays on screen as the page scrolls), a hint to scroll, and the legal links
 * at the bottom. [entranceProgress] runs from 0 to 1 as the hero fades up on first load.
 */
@Composable
fun HeroPage(
    modifier: Modifier = Modifier,
    frame: LandingFrame,
    textModifier: Modifier = Modifier,
    entranceProgress: () -> Float,
    animateHint: Boolean,
    onScrollHintClick: () -> Unit,
    onNavigate: OnNavigate,
) {
    val isWide = frame.layout == SideBySide
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = entranceProgress()
                alpha = progress
                translationY = (1f - progress) * WebDimensions.entranceRise.toPx()
            }
            .padding(horizontal = MaterialTheme.spacing.medium),
    ) {
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = frame.heroTextTop).then(textModifier)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Stay sharp, study smart",
                    style = if (isWide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Sharpen your dev skills: curated flashcards for Kotlin, Android, Python and more",
                    style = if (isWide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small).widthIn(max = WebDimensions.heroDescriptionMaxWidth),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            ScrollHint(animate = animateHint, onClick = onScrollHintClick)
            SiteLinks(showContact = false, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun ScrollHint(animate: Boolean, onClick: () -> Unit) {
    val bob = if (animate) rememberHintBob() else remember { mutableFloatStateOf(0f) }
    Column(
        modifier = Modifier
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(MaterialTheme.spacing.xsmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Scroll to explore",
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
        )
        val chevronColor = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA)
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
