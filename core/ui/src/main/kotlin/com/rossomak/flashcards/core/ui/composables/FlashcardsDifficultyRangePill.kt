package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.DifficultyColors
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.difficultyColors

private val RANGE_PILL_HEIGHT = 24.dp

/** Fraction of the pill's width each end color holds flat before the gradient blends. */
private const val GRADIENT_STOP_FRACTION = 0.38f

/**
 * Pill summarizing a difficulty range [lowLevel]–[highLevel] as a two-stop [DifficultyColors]
 * gradient — flat at each end, blending across the middle — used wherever a filter or session
 * setup shows the selected difficulty band at a glance.
 */
@Composable
fun FlashcardsDifficultyRangePill(
    lowLevel: Int,
    highLevel: Int,
    modifier: Modifier = Modifier,
) {
    val lowColor = MaterialTheme.difficultyColors.colorFor(lowLevel)
    val highColor = MaterialTheme.difficultyColors.colorFor(highLevel)
    val gradient = Brush.horizontalGradient(
        GRADIENT_STOP_FRACTION to lowColor,
        1f - GRADIENT_STOP_FRACTION to highColor,
    )
    val description = stringResource(
        R.string.common_difficulty_range_cd,
        lowLevel,
        highLevel,
        DifficultyColors.LEVELS,
    )
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.full)
    Row(
        modifier = modifier
            .height(RANGE_PILL_HEIGHT)
            .background(brush = gradient, shape = shape)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$lowLevel–$highLevel",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.difficultyColors.onRamp,
        )
    }
}

@ShowkaseComposable(name = "Difficulty range pill", group = "Chips & Badges")
@PreviewLightDark
@Composable
fun FlashcardsDifficultyRangePillShowcase() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsDifficultyRangePill(lowLevel = 1, highLevel = 10)
                FlashcardsDifficultyRangePill(lowLevel = 3, highLevel = 6)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsDifficultyRangePillPreview() {
    FlashcardsTheme {
        Surface {
            FlashcardsDifficultyRangePill(lowLevel = 5, highLevel = 8)
        }
    }
}
