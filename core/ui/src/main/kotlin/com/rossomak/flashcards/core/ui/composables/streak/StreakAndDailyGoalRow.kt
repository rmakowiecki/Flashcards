package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

private val STREAK_AND_DAILY_GOAL_MIN_CARD_WIDTH = 150.dp
private val STREAK_AND_DAILY_GOAL_PREVIEW_NARROW_WIDTH = 280.dp

/** [StreakCard] and [DailyGoalCard] side by side at equal size, stacked when the width per card is too narrow; never by font scale. */
@Composable
fun StreakAndDailyGoalRow(
    streakDays: Int,
    bestStreakDays: Int,
    isStreakAtRisk: Boolean,
    studiedMinutes: Int,
    goalMinutes: Int,
    goalPercent: Int,
    isGoalMet: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardSpacing = MaterialTheme.spacing.small

        if (streakAndDailyGoalCardsStack(availableWidth = maxWidth, cardSpacing = cardSpacing)) {
            Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
                StreakCard(days = streakDays, bestDays = bestStreakDays, isAtRisk = isStreakAtRisk, modifier = Modifier.fillMaxWidth())
                DailyGoalCard(
                    studiedMinutes = studiedMinutes,
                    goalMinutes = goalMinutes,
                    percent = goalPercent,
                    isGoalMet = isGoalMet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            ) {
                StreakCard(
                    days = streakDays,
                    bestDays = bestStreakDays,
                    isAtRisk = isStreakAtRisk,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DailyGoalCard(
                    studiedMinutes = studiedMinutes,
                    goalMinutes = goalMinutes,
                    percent = goalPercent,
                    isGoalMet = isGoalMet,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

internal fun streakAndDailyGoalCardsStack(availableWidth: Dp, cardSpacing: Dp): Boolean =
    (availableWidth - cardSpacing) / 2 < STREAK_AND_DAILY_GOAL_MIN_CARD_WIDTH

@ShowkaseComposable(name = "Streak and daily goal row", group = "Level")
@Preview
@Composable
fun StreakAndDailyGoalRowShowcase() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PreviewRow(modifier = Modifier.padding(MaterialTheme.spacing.normal))
        }
    }
}

@Composable
private fun PreviewRow(modifier: Modifier = Modifier) {
    StreakAndDailyGoalRow(
        streakDays = 8,
        bestStreakDays = 14,
        isStreakAtRisk = false,
        studiedMinutes = 14,
        goalMinutes = 20,
        goalPercent = 70,
        isGoalMet = false,
        modifier = modifier,
    )
}

@PreviewLightDark
@Preview(fontScale = 2f)
@Composable
private fun StreakAndDailyGoalRowPreview() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                PreviewRow()
                PreviewRow(modifier = Modifier.width(STREAK_AND_DAILY_GOAL_PREVIEW_NARROW_WIDTH))
            }
        }
    }
}
