@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryContent
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScreenState
import com.rossomak.flashcards.feature.study.summary.XpAwardSource
import com.rossomak.flashcards.feature.study.summary.XpBreakdownLine

private val xpLines = listOf(
    XpBreakdownLine(XpAwardSource.NewCards, count = 6, rate = 20, amount = 120),
    XpBreakdownLine(XpAwardSource.Mastered, count = 11, rate = 100, amount = 1_100),
    XpBreakdownLine(XpAwardSource.Partial, count = 3, rate = 25, amount = 75),
    XpBreakdownLine(XpAwardSource.MasteryDefended, count = 2, rate = 50, amount = 100),
    XpBreakdownLine(XpAwardSource.Streak, count = ShowcaseUser.CURRENT_STREAK, rate = null, amount = 250),
    XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 100),
)

/** A Rated session that crossed into Ross's Level. Already scored, so the Summary skips its animation. */
private val summaryState = StudySessionSummaryScreenState(
    mode = StudyMode.Rated,
    durationSeconds = 840,
    studiedCount = 20,
    masteredCount = 11,
    headline = StudySessionSummaryHeadline.GreatWork,
    scoreStatus = StudySessionSummaryScoreStatus.Scored,
    xpLines = xpLines,
    xpTotal = xpLines.sumOf { it.amount },
    level = ShowcaseUser.LEVEL,
    xpIntoCurrentLevel = ShowcaseUser.XP_INTO_LEVEL,
    xpForNextLevel = ShowcaseUser.XP_FOR_NEXT_LEVEL,
    levelsCrossed = listOf(ShowcaseUser.LEVEL),
    levelBefore = ShowcaseUser.LEVEL - 1,
    xpIntoCurrentLevelBefore = 6_505,
    xpForNextLevelBefore = 6_750,
    currentStreak = ShowcaseUser.CURRENT_STREAK,
    hasAuthUserResolved = true,
    displayName = ShowcaseUser.DISPLAY_NAME,
)

@Composable
internal fun StudySessionSummaryShowcase(onNavigateBack: () -> Unit) {
    StudySessionSummaryContent(
        state = summaryState,
        onNavigateBack = onNavigateBack,
        onStudyAgainClick = {},
        onDialogEvent = {},
    )
}
