package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.animation.SharedElementKey
import com.rossomak.flashcards.core.ui.animation.sharedElementByKey
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingContentColors
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn

private val LogoWidth = 200.dp
private val LogoHeight = LogoWidth * (1000f / 1800f)

/** How far below its resting place the copy starts before it slides up into view. */
private val CopyRevealOffset = 24.dp

/**
 * The cover: brand mark, promise, and what the next few screens will cover. No decisions here.
 *
 * The logo is the shared element the splash screen hands over, so it is already on screen when this
 * step composes and must not animate with the rest. [copyRevealProgress] drives everything below it
 * — `0f` is fully hidden and shifted down, `1f` is settled — and is owned by the pager's host so the
 * copy, then the buttons, arrive in sequence behind the logo landing.
 */
@Composable
internal fun WelcomeStep(copyRevealProgress: Float, modifier: Modifier = Modifier) {
    OnboardingStepColumn(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(com.rossomak.flashcards.core.ui.R.drawable.flashcards_white),
            contentDescription = null,
            modifier = Modifier
                .sharedElementByKey(SharedElementKey.APP_LOGO)
                .size(width = LogoWidth, height = LogoHeight),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xxlarge))
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = copyRevealProgress
                translationY = (1f - copyRevealProgress) * CopyRevealOffset.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome_headline_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = OnboardingContentColors.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
            Text(
                text = stringResource(R.string.welcome_intro_message),
                style = MaterialTheme.typography.bodyMedium,
                color = OnboardingContentColors.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
