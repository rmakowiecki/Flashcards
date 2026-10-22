package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.EmphasizedSpanStyle
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

private val STREAK_CARD_FLAME_SIZE = 32.dp

/** Read-only Streak card. The flame dims while [isAtRisk], meaning the Streak is alive but today doesn't count yet. */
@Composable
fun StreakCard(days: Int, bestDays: Int, isAtRisk: Boolean, modifier: Modifier = Modifier) {
    val daysDescription = pluralStringResource(R.plurals.streak_card_days_cd, days, days)
    val bestDaysDescription = pluralStringResource(R.plurals.streak_card_days_cd, bestDays, bestDays)
    val description = when {
        isAtRisk -> stringResource(R.string.streak_card_at_risk_cd, daysDescription, bestDaysDescription)
        else -> stringResource(R.string.streak_card_cd, daysDescription, bestDaysDescription)
    }
    val flameColor = when {
        isAtRisk -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.brandColors.streakFlame
    }
    val bestText = stringResource(R.string.streak_card_best_label, bestDays)
    val bestLabel = remember(bestText, bestDays) { emphasizeValue(bestText, bestDays.toString()) }

    DailyProgressCardFrame(
        title = stringResource(R.string.streak_card_title),
        description = description,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(STREAK_CARD_FLAME_SIZE),
                tint = flameColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall)) {
                Text(
                    text = days.toString(),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
                Text(
                    text = pluralStringResource(R.plurals.streak_card_days_label, days),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = bestLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * [text] with [value] and the letters right after it emphasized, so "Best: 14d" reads "Best: **14d**" in
 * any word order a translation picks. Returns [text] plain when [value] is not in it.
 */
internal fun emphasizeValue(text: String, value: String): AnnotatedString {
    val start = text.indexOf(value)
    if (start < 0) return AnnotatedString(text)
    val end = (start + value.length until text.length).firstOrNull { !text[it].isLetter() } ?: text.length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(EmphasizedSpanStyle) { append(text.substring(start, end)) }
        append(text.substring(end))
    }
}

@PreviewLightDark
@Composable
private fun StreakCardPreview() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.normal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                StreakCard(days = 8, bestDays = 14, isAtRisk = false, modifier = Modifier.fillMaxWidth())
                StreakCard(days = 8, bestDays = 14, isAtRisk = true, modifier = Modifier.fillMaxWidth())
                StreakCard(days = 0, bestDays = 14, isAtRisk = false, modifier = Modifier.fillMaxWidth())
                StreakCard(days = 1, bestDays = 1, isAtRisk = false, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
