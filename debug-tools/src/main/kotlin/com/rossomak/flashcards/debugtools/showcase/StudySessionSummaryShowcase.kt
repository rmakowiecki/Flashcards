@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.debugtools.showcase.SummaryShowcaseState.Confetti
import com.rossomak.flashcards.debugtools.showcase.SummaryShowcaseState.Settled
import com.rossomak.flashcards.debugtools.showcase.SummaryShowcaseState.XpBreakdown
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseUser
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryContent
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScreenState
import com.rossomak.flashcards.feature.study.summary.SummaryLayout
import com.rossomak.flashcards.feature.study.summary.SummaryPhase
import com.rossomak.flashcards.feature.study.summary.SummarySequence
import com.rossomak.flashcards.feature.study.summary.XpAwardSource
import com.rossomak.flashcards.feature.study.summary.XpBreakdownLine
import com.rossomak.flashcards.feature.study.summary.drawBurst
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** Every award but a de-mastery, at the default XP configuration's rates. */
private val xpLines = listOf(
    XpBreakdownLine(XpAwardSource.NewCards, count = 12, rate = 10, amount = 120),
    XpBreakdownLine(XpAwardSource.Mastered, count = 11, rate = 100, amount = 1_100),
    XpBreakdownLine(XpAwardSource.Partial, count = 3, rate = 25, amount = 75),
    XpBreakdownLine(XpAwardSource.MasteryDefended, count = 2, rate = 50, amount = 100),
    XpBreakdownLine(XpAwardSource.Streak, count = ShowcaseUser.CURRENT_STREAK, rate = null, amount = 1_000),
    XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 100),
    XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = 500),
)

private val xpTotal = xpLines.sumOf { it.amount }

/** Crosses into Ross's Level, ending where Home's Level card stands. The day's first session, so it meets the Daily Goal alone. */
private val summaryState = StudySessionSummaryScreenState(
    mode = StudyMode.Rated,
    durationSeconds = 1_320,
    studiedCount = 20,
    masteredCount = 11,
    headline = StudySessionSummaryHeadline.GreatWork,
    scoreStatus = StudySessionSummaryScoreStatus.Scored,
    xpLines = xpLines,
    xpTotal = xpTotal,
    level = ShowcaseUser.LEVEL,
    xpIntoCurrentLevel = ShowcaseUser.XP_INTO_LEVEL,
    xpForNextLevel = ShowcaseUser.XP_FOR_NEXT_LEVEL,
    levelsCrossed = listOf(ShowcaseUser.LEVEL),
    levelBefore = ShowcaseUser.LEVEL - 1,
    xpIntoCurrentLevelBefore = 6_750 - (xpTotal - ShowcaseUser.XP_INTO_LEVEL),
    xpForNextLevelBefore = 6_750,
    currentStreak = ShowcaseUser.CURRENT_STREAK,
    hasAuthUserResolved = true,
    displayName = ShowcaseUser.DISPLAY_NAME,
)

/** Lets the Level card settle before the burst is placed on the total. */
private val RevealSettleDelay = 2.seconds

/** A frame where every particle is spread out and fully opaque. */
private const val FROZEN_BURST_ELAPSED_MILLIS = 600f

/** [Settled] opens scored, skipping the sequence. [XpBreakdown] and [Confetti] hold the sequence on one step. */
@Composable
internal fun StudySessionSummaryShowcase(showcaseState: SummaryShowcaseState, onNavigateBack: () -> Unit) {
    when (showcaseState) {
        Settled -> {
            var state by remember { mutableStateOf(summaryState) }
            StudySessionSummaryContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onStudyAgainClick = {},
                onDialogEvent = { event -> state = state.copy(activeDialog = showcaseDialogAfter(event)) },
            )
        }

        XpBreakdown -> HeldSummary(sequence = remember { heldSequence(SummaryPhase.Counting) }, onNavigateBack = onNavigateBack)

        Confetti -> Box(modifier = Modifier.fillMaxSize()) {
            val sequence = remember { heldSequence(SummaryPhase.Revealing) }
            HeldSummary(sequence = sequence, onNavigateBack = onNavigateBack)
            FrozenConfetti(sequence)
        }
    }
}

private fun heldSequence(phase: SummaryPhase) = SummarySequence(xpLines, xpTotal, phase, visibleLineCount = xpLines.size)

@Composable
private fun HeldSummary(sequence: SummarySequence, onNavigateBack: () -> Unit) {
    SummaryLayout(
        state = summaryState,
        sequence = sequence,
        snackbarHostState = remember { SnackbarHostState() },
        onNavigateBack = onNavigateBack,
        onStudyAgainClick = {},
        onDialogEvent = {},
    )
}

@Composable
private fun FrozenConfetti(sequence: SummarySequence) {
    var burstCenter by remember { mutableStateOf<Offset?>(null) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(sequence) {
        delay(RevealSettleDelay)
        burstCenter = sequence.totalTextCoordinates?.takeIf { it.isAttached }?.boundsInWindow()?.center
    }
    val center = burstCenter ?: return
    Canvas(modifier = Modifier.fillMaxSize().onPlaced { coordinates -> canvasOrigin = coordinates.positionInWindow() }) {
        drawBurst(center - canvasOrigin, FROZEN_BURST_ELAPSED_MILLIS)
    }
}
