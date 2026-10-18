package com.rossomak.flashcards.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.ui.composables.FlashcardsStepper
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleActionDialog

/**
 * Picks the daily study-time goal. Settings-only — nothing session-scoped reads
 * [dailyGoalMinutes][com.rossomak.flashcards.core.domain.model.UserPreferences.dailyGoalMinutes],
 * so unlike the study-session dialogs this one never takes a `keepAsDefault`: the value here *is*
 * the default, always.
 *
 * Deferred commit, same as every other Settings dialog — the stepper edits [draft] and nothing is
 * applied until [onConfirm].
 */
@Composable
internal fun DailyGoalDialog(
    draft: Int,
    onDraftChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.settings_daily_goal_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            FlashcardsStepper(
                value = draft,
                onDecrement = { onDraftChange((draft - DailyGoal.STEP_MINUTES).coerceAtLeast(DailyGoal.MIN_MINUTES)) },
                onIncrement = { onDraftChange((draft + DailyGoal.STEP_MINUTES).coerceAtMost(DailyGoal.MAX_MINUTES)) },
                decrementContentDescription = stringResource(R.string.settings_daily_goal_decrement_cd),
                incrementContentDescription = stringResource(R.string.settings_daily_goal_increment_cd),
                decrementEnabled = draft > DailyGoal.MIN_MINUTES,
                incrementEnabled = draft < DailyGoal.MAX_MINUTES,
            )
            Text(
                text = stringResource(R.string.settings_daily_goal_unit_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun DailyGoalDialogPreview() {
    DailyGoalDialog(
        draft = DailyGoal.DEFAULT_MINUTES,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}
