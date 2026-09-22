package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Emphasized
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.rating.FlashcardsRatingButtonRow
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingHierarchyConnector
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader

/**
 * Attempts and Ratings on the illustrated card. Deliberately generic about how many Attempts a card
 * gets: the limit is user-configurable in Settings (default 3, max 5), so neither the copy nor this
 * illustration may state a fixed number as a rule.
 */
private val ILLUSTRATED_ATTEMPTS = listOf(
    FlashcardsAttemptSlotState.Failed,
    FlashcardsAttemptSlotState.Partial,
    FlashcardsAttemptSlotState.Future,
)

/** XP awarded for mastering one card, per docs/design/xp-leveling-system.md. */
private const val CARD_MASTERY_XP = 100

/** Difficulty shown on the illustrated card — mid-range, so the badge reads as neither extreme. */
private const val ILLUSTRATED_DIFFICULTY = 4

/**
 * Explains the Attempt → Rating → Terminal State mechanic. Presentation only: the rating row is
 * rendered read-only, since there is nothing here to actually rate.
 *
 * Illustrated as three stacked cards — attempt/difficulty and the question, then the rating
 * prompt, then the mastery result — connected by the same chevron used to walk the Category →
 * Subcategory → Flashcard hierarchy in [StructureStep], since both are a sequence of steps rather than
 * one flat card.
 */
@Composable
internal fun MasteryStep(modifier: Modifier = Modifier) {
    OnboardingStepColumn(modifier = modifier) {
        OnboardingStepHeader(
            eyebrow = stringResource(R.string.mastery_eyebrow_label),
            headline = stringResource(R.string.mastery_headline_title),
            message = stringResource(R.string.mastery_intro_message),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        MasteryQuestionCard()
        OnboardingHierarchyConnector()
        MasteryRatingCard()
        OnboardingHierarchyConnector()
        MasteryResultCard()
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsInfoBanner(
            text = stringResource(R.string.mastery_xp_banner_message),
            icon = Icons.Default.WorkspacePremium,
            modifier = Modifier.fillMaxWidth(),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

@Composable
private fun MasteryQuestionCard(modifier: Modifier = Modifier) {
    MasteryCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelledSlot(label = stringResource(R.string.mastery_attempts_label)) {
                FlashcardsAttemptIndicator(slots = ILLUSTRATED_ATTEMPTS)
            }
            LabelledSlot(
                label = stringResource(R.string.mastery_difficulty_label),
                horizontalAlignment = Alignment.End,
            ) {
                FlashcardsDifficultyBadge(level = ILLUSTRATED_DIFFICULTY)
            }
        }
        Text(
            text = stringResource(R.string.mastery_example_question_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MasteryRatingCard(modifier: Modifier = Modifier) {
    MasteryCard(modifier = modifier) {
        Text(
            text = stringResource(CoreUiR.string.common_rating_prompt_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        FlashcardsRatingButtonRow()
    }
}

@Composable
private fun MasteryResultCard(modifier: Modifier = Modifier) {
    MasteryCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.small,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIndicatorBadge(
                label = stringResource(R.string.mastery_mastered_label),
                emphasis = Emphasized,
            )
            Text(
                text = stringResource(R.string.mastery_xp_reward_label, CARD_MASTERY_XP),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MasteryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.normal),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            content = content,
        )
    }
}

@Composable
private fun LabelledSlot(
    label: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
