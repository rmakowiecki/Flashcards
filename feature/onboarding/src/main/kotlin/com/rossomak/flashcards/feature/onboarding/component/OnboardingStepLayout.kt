package com.rossomak.flashcards.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.rossomak.flashcards.core.ui.theme.spacing

/** Letter spacing of the all-caps eyebrow label. */
private val EyebrowLetterSpacing = 1.2.sp

/**
 * The vertical rhythm every step shares: one scrollable column, so a step whose content outgrows a
 * short screen (or grows under a large font scale) scrolls within its own page instead of forcing
 * the pager to shrink its content.
 */
@Composable
fun OnboardingStepColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.medium),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Eyebrow / headline / supporting copy, the header every step after the cover shares. [eyebrow] is
 * omitted on steps the design gives no category label.
 */
@Composable
fun OnboardingStepHeader(
    headline: String,
    message: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        if (eyebrow != null) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = OnboardingContentColors.eyebrow,
                letterSpacing = EyebrowLetterSpacing,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnboardingContentColors.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = OnboardingContentColors.secondary,
            textAlign = TextAlign.Center,
        )
    }
}
