package com.rossomak.flashcards.feature.study.summary

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDialog.XpBreakdown
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.NiceEffort
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.PerfectRun
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable

private val FastScoredLines = listOf(
    XpBreakdownLine(XpAwardSource.NewCards, count = 12, rate = 20, amount = 240),
    XpBreakdownLine(XpAwardSource.TimeStudied, count = 4, rate = 10, amount = 40),
    XpBreakdownLine(XpAwardSource.Streak, count = 3, rate = null, amount = 250),
    XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
)

private val EveryLine = listOf(
    XpBreakdownLine(XpAwardSource.NewCards, count = 1, rate = 20, amount = 20),
    XpBreakdownLine(XpAwardSource.Mastered, count = 8, rate = 200, amount = 1_600),
    XpBreakdownLine(XpAwardSource.Partial, count = 2, rate = 25, amount = 50),
    XpBreakdownLine(XpAwardSource.MasteryDefended, count = 3, rate = 50, amount = 150),
    XpBreakdownLine(XpAwardSource.MasteryLost, count = 2, rate = -80, amount = -160),
    XpBreakdownLine(XpAwardSource.TimeStudied, count = 12, rate = 10, amount = 120),
    XpBreakdownLine(XpAwardSource.Streak, count = 8, rate = null, amount = 250),
    XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
    XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = 1_000),
)

private val PerfectRunLines = EveryLine.filter { it.source != XpAwardSource.MasteryLost }

private val ScoredState = StudySessionSummaryScreenState(
    scoreStatus = Scored,
    level = 7,
    xpIntoCurrentLevel = 2_300,
    xpForNextLevel = 6_000,
    displayName = "Jane Doe",
)

@Composable
private fun SummaryPreview(state: StudySessionSummaryScreenState) {
    FlashcardsTheme {
        StudySessionSummaryContent(
            state = state,
            onNavigateBack = {},
            onStudyAgainClick = {},
            onDialogEvent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryFastScoredPreview() {
    SummaryPreview(
        ScoredState.copy(
            mode = StudyMode.Fast,
            durationSeconds = 240,
            studiedCount = 12,
            xpLines = FastScoredLines,
            xpTotal = 1_030,
        ),
    )
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryRatedPerfectRunPreview() {
    SummaryPreview(
        ScoredState.copy(
            durationSeconds = 600,
            studiedCount = 8,
            masteredCount = 8,
            headline = PerfectRun,
            xpLines = PerfectRunLines,
            xpTotal = PerfectRunLines.sumOf { it.amount },
        ),
    )
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryRatedLossPreview() {
    SummaryPreview(
        ScoredState.copy(
            durationSeconds = 50,
            studiedCount = 6,
            masteredCount = 1,
            abandoned = true,
            headline = NiceEffort,
            xpLines = listOf(
                XpBreakdownLine(XpAwardSource.MasteryLost, count = 3, rate = -80, amount = -240),
            ),
            xpTotal = -240,
        ),
    )
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryUnavailablePreview() {
    SummaryPreview(
        StudySessionSummaryScreenState(
            durationSeconds = 300,
            studiedCount = 5,
            masteredCount = 3,
            scoreStatus = Unavailable,
        ),
    )
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryLoadingPreview() {
    SummaryPreview(StudySessionSummaryScreenState(scoreStatus = Loading))
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryXpBreakdownDialogPreview() {
    SummaryPreview(
        ScoredState.copy(
            durationSeconds = 720,
            studiedCount = 14,
            masteredCount = 8,
            xpLines = EveryLine,
            xpTotal = EveryLine.sumOf { it.amount },
            activeDialog = XpBreakdown,
        ),
    )
}

private val LevelUpState = ScoredState.copy(
    durationSeconds = 720,
    studiedCount = 14,
    masteredCount = 8,
    xpLines = EveryLine,
    xpTotal = EveryLine.sumOf { it.amount },
    level = 8,
    xpIntoCurrentLevel = 1_100,
    xpForNextLevel = 7_000,
    levelsCrossed = listOf(8),
    levelBefore = 7,
    xpIntoCurrentLevelBefore = 5_200,
    xpForNextLevelBefore = 6_000,
)

private const val MID_ITEMIZATION_LINES = 5
private const val MID_POUR_XP = 5_700L

/** A frame of the payoff held still, drawn through the same layout the live sequence uses. */
@Composable
private fun PayoffFramePreview(state: StudySessionSummaryScreenState, frame: SummaryFrame) {
    val payoff = remember {
        SummaryPayoff(state.xpLines, state.xpTotal, state.positionBefore(), state.positionAfter(), state.levelsCrossed, frame)
    }
    FlashcardsTheme {
        SummaryLayout(
            state = state,
            payoff = payoff,
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onStudyAgainClick = {},
            onDialogEvent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryMidItemizationPreview() {
    val shownLines = EveryLine.take(MID_ITEMIZATION_LINES)
    PayoffFramePreview(
        state = LevelUpState,
        frame = SummaryFrame.start(LevelUpState.positionBefore()).copy(
            runningTotal = shownLines.sumOf { it.amount }.toFloat(),
            visibleLineCount = shownLines.size,
        ),
    )
}

@PreviewLightDark
@Composable
private fun StudySessionSummaryMidPourPreview() {
    PayoffFramePreview(
        state = LevelUpState,
        frame = SummaryFrame.settled(LevelUpState.xpTotal, LevelUpState.xpLines.size, LevelUpState.positionBefore()).copy(
            pour = LevelPosition(level = LevelUpState.levelBefore, xpIntoLevel = MID_POUR_XP, xpForNextLevel = LevelUpState.xpForNextLevelBefore),
            isSettled = false,
        ),
    )
}
