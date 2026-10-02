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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.rossomak.flashcards.core.ui.composables.FlashcardsStepper
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader

/**
 * Sets the Daily Goal, and shows what a streak looks like once one exists.
 *
 * The streak card is an illustration on its own surface, labelled EXAMPLE, and is deliberately not
 * merged into the goal card below it: a brand-new user's real streak is zero, and a fabricated
 * figure rendered in the same surface as a live control would read as their own statistic.
 */
@Composable
internal fun DailyGoalStep(
    dailyGoalMinutes: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepColumn(modifier = modifier) {
        OnboardingStepHeader(
            eyebrow = stringResource(R.string.daily_goal_eyebrow_label),
            headline = stringResource(R.string.daily_goal_headline_title),
            message = stringResource(R.string.daily_goal_intro_message),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        StreakExampleCard()
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
        DailyGoalCard(
            dailyGoalMinutes = dailyGoalMinutes,
            canDecrement = canDecrement,
            canIncrement = canIncrement,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsInfoBanner(
            text = stringResource(R.string.daily_goal_xp_banner_message),
            icon = Icons.Default.EmojiEvents,
            modifier = Modifier.fillMaxWidth(),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

@Composable
private fun StreakExampleCard(modifier: Modifier = Modifier) {
    OnboardingCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.daily_goal_example_label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.daily_goal_streak_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.daily_goal_streak_example_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.daily_goal_streak_example_message),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyGoalCard(
    dailyGoalMinutes: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.daily_goal_goal_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlashcardsStepper(
            value = dailyGoalMinutes,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
            decrementContentDescription = stringResource(R.string.daily_goal_decrease_cd),
            incrementContentDescription = stringResource(R.string.daily_goal_increase_cd),
            decrementEnabled = canDecrement,
            incrementEnabled = canIncrement,
        )
        Text(
            text = stringResource(R.string.daily_goal_unit_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingCard(
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
            content = content,
        )
    }
}
