package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Emphasized
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Neutral
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * A pill badge that reads as one of two states rather than a plain label — [Emphasized] (e.g. a
 * Mastered result, the currently active card phase) or [Neutral] (e.g. a card phase that isn't
 * active right now). Used for onboarding's Mastery result and, in [Emphasized]/[Neutral] pairs,
 * for the QUESTION/ANSWER phase indicators on [feature:study]'s FlashcardCard.
 */
@Composable
fun FlashcardsIndicatorBadge(
    label: String,
    emphasis: FlashcardsIndicatorEmphasis,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (emphasis) {
        Emphasized -> MaterialTheme.colorScheme.secondaryContainer
        Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (emphasis) {
        Emphasized -> MaterialTheme.colorScheme.onSecondaryContainer
        Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.full),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.xxsmall,
            ),
        )
    }
}

@ShowkaseComposable(name = "Indicator badge", group = "Chips & Badges")
@Composable
fun FlashcardsIndicatorBadgeShowcase() {
    FlashcardsTheme {
        Surface {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsIndicatorBadge(label = "QUESTION", emphasis = Emphasized)
                FlashcardsIndicatorBadge(label = "ANSWER", emphasis = Neutral)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsIndicatorBadgePreview() {
    FlashcardsTheme {
        Surface {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsIndicatorBadge(label = "QUESTION", emphasis = Emphasized)
                FlashcardsIndicatorBadge(label = "ANSWER", emphasis = Neutral)
            }
        }
    }
}
