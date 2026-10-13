package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.DifficultyColors
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.difficultyColors
import com.rossomak.flashcards.core.ui.theme.spacing

private val DIFFICULTY_BADGE_SIZE = 24.dp

/**
 * Circular badge showing a card's [level] (1 easiest – 10 hardest) filled with its
 * [DifficultyColors] ramp stop. Fixed white numeral — every ramp stop is saturated enough for
 * contrast, so this does not follow the surface/onSurface pair like [FlashcardsMetadataBadge].
 */
@Composable
fun FlashcardsDifficultyBadge(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = DIFFICULTY_BADGE_SIZE,
) {
    val description = stringResource(R.string.common_difficulty_level_cd, level, DifficultyColors.LEVELS)
    Box(
        modifier = modifier
            .size(size)
            .background(color = MaterialTheme.difficultyColors.colorFor(level), shape = CircleShape)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.difficultyColors.onRamp,
        )
    }
}

@ShowkaseComposable(name = "Difficulty badge — all stops", group = "Chips & Badges")
@PreviewLightDark
@Composable
fun FlashcardsDifficultyBadgeShowcase() {
    FlashcardsTheme {
        Surface {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                for (level in 1..DifficultyColors.LEVELS) {
                    FlashcardsDifficultyBadge(level = level)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsDifficultyBadgePreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsDifficultyBadge(level = 8)
        }
    }
}
