package com.rossomak.flashcards.feature.study.summary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.ui.format.formatSignedXp
import com.rossomak.flashcards.core.ui.format.formatXpFactor
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.DailyGoal
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.Mastered
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.MasteryDefended
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.MasteryLost
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.NewCards
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.Partial
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.SessionCompleted
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.Streak
import com.rossomak.flashcards.feature.study.summary.XpAwardSource.TimeStudied
import java.util.Locale

/** The name of the rule behind [source], e.g. "Mastery Defended". */
@Composable
internal fun xpSourceLabel(source: XpAwardSource): String = stringResource(
    when (source) {
        NewCards -> R.string.study_session_summary_xp_row_new_cards_label
        Mastered -> R.string.study_session_summary_xp_row_mastered_label
        Partial -> R.string.study_session_summary_xp_row_partial_label
        MasteryDefended -> R.string.study_session_summary_xp_row_mastery_defended_label
        MasteryLost -> R.string.study_session_summary_xp_row_mastery_lost_label
        TimeStudied -> R.string.study_session_summary_xp_row_time_studied_label
        Streak -> R.string.study_session_summary_xp_row_streak_label
        SessionCompleted -> R.string.study_session_summary_xp_row_session_completed_label
        DailyGoal -> R.string.study_session_summary_xp_row_daily_goal_label
    },
)

internal fun xpSourceIcon(source: XpAwardSource): ImageVector = when (source) {
    NewCards -> Icons.Filled.Add
    Mastered -> Icons.Filled.WorkspacePremium
    Partial -> Icons.Filled.Star
    MasteryDefended -> Icons.Filled.Shield
    MasteryLost -> Icons.AutoMirrored.Filled.TrendingDown
    TimeStudied -> Icons.Filled.Schedule
    Streak -> Icons.Filled.LocalFireDepartment
    SessionCompleted -> Icons.Filled.CheckCircle
    DailyGoal -> Icons.Filled.EmojiEvents
}

/**
 * What [line]'s amount is made of, without the amount: "3 cards × 20", "2 min × 10", "8-day streak"
 * or a caption such as "Session completed" for an award that is not a product.
 */
@Composable
internal fun xpLineFormula(line: XpBreakdownLine, locale: Locale): String {
    val count = line.count ?: 0
    val rate = formatXpFactor(line.rate ?: 0, locale)
    return when (line.source) {
        NewCards, Mastered, Partial, MasteryDefended, MasteryLost ->
            pluralStringResource(R.plurals.study_session_summary_xp_formula_cards_label, count, count, rate)
        TimeStudied -> stringResource(R.string.study_session_summary_xp_formula_minutes_label, formatXpFactor(count, locale), rate)
        Streak -> stringResource(R.string.study_session_summary_xp_formula_streak_label, count)
        SessionCompleted -> stringResource(R.string.study_session_summary_xp_formula_session_completed_label)
        DailyGoal -> stringResource(R.string.study_session_summary_xp_formula_daily_goal_label)
    }
}

/** [xpLineFormula] and the signed amount it adds up to: "3 cards × 20 = +60 XP". */
@Composable
internal fun xpLineValue(line: XpBreakdownLine, locale: Locale): String =
    stringResource(R.string.study_session_summary_xp_row_value_label, xpLineFormula(line, locale), formatSignedXp(line.amount, locale))
