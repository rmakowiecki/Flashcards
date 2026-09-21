package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionXpResult
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.toSessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reads the terminated session's result straight from the route arguments — the only load path this
 * route ever carries (fresh-session egress only, never a past session) — and
 * submits it once, on arrival (ADR-0014, superseded for the write path by the server-authoritative session commit).
 *
 * The submission fires from `init`, which Hilt/Compose Navigation only run once per back-stack entry:
 * this ViewModel survives configuration change, so there is no separate "have I already submitted"
 * flag to maintain. There is likewise no branch to skip a past-session load: [StudySessionSummaryRoute]
 * has no sessionId-only shape today, only ever a complete [SessionResult][com.rossomak.flashcards.core.domain.model.SessionResult] —
 * a future past-session detail view is a separate screen and route (ADR-0014), not a branch of this one.
 */
@HiltViewModel
class StudySessionSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val submitStudySession: SubmitStudySessionUseCase,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<StudySessionSummaryRoute>()

    private val _state = MutableStateFlow(StudySessionSummaryScreenState())
    val state: StateFlow<StudySessionSummaryScreenState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<StudySessionSummaryMessage>(extraBufferCapacity = 1)

    val messages: SharedFlow<StudySessionSummaryMessage> = _messages.asSharedFlow()

    init {
        // Written directly in init, not inside submitSession()'s coroutine: this must land before
        // the ViewModel instance is handed back to hiltViewModel(), so the very first state Compose
        // ever observes already carries the real mode/counts — never the StudySessionSummaryScreenState
        // default. All five fields sit on `route` already (no dailyGoalMinutes/I-O dependency), so
        // there is no reason to make them wait behind submitSession()'s async read. Getting this
        // wrong previously let a screen-composition race latch the summary's Ring/XpPour phase (and
        // its headline/meta text) onto the default StudyMode.Rated for one frame, e.g. flashing the
        // mastery ring on a genuine Fast session before the real mode arrived.
        //
        // 0/0/0 for a Fast result is a UI-state convention only (see StudySessionSummaryScreenState's
        // own KDoc) — the screen chooses its layout off `mode`, never off these being zero. The
        // domain SessionResult itself has no such fields on its Fast branch at all (sealed).
        val terminalStateCounts = when (route.mode) {
            StudyMode.Rated -> Triple(
                route.cardStates.count { it == FlashcardStudyProgressState.Mastered },
                route.cardStates.count { it == FlashcardStudyProgressState.Partial },
                route.cardStates.count { it == FlashcardStudyProgressState.Failed },
            )
            StudyMode.Fast -> Triple(0, 0, 0)
        }
        _state.update {
            it.copy(
                mode = route.mode,
                durationSeconds = route.durationSeconds,
                studiedCount = route.cardIds.size,
                abandoned = route.abandoned,
                masteredCount = terminalStateCounts.first,
                partialCount = terminalStateCounts.second,
                failedCount = terminalStateCounts.third,
            )
        }
        submitSession()
    }

    /**
     * Reconstructs the terminated session's [SessionResult] from the route arguments — the only load
     * path this route ever carries (fresh-session egress only, never a past
     * session) — and submits it once, on arrival (ADR-0014, superseded for the write path by the server-authoritative session commit).
     * `init` only runs once per back-stack entry (this ViewModel survives configuration change), so
     * there is no separate "have I already submitted" flag to maintain.
     *
     * [studyDate] and [studyDateUtcOffsetMinutes] are [toSessionResult]'s own concern now — both
     * derived purely from the route (the offset was captured at session start, not here). Only
     * [dailyGoalMinutes] is captured **here**, once, right before [toSessionResult]: a fresh local
     * preferences read, not re-read at eventual delivery time if the session sits in the offline queue
     * (ADR-0048), baked into the immutable [SessionResult] from this point on.
     *
     * [SubmitStudySessionUseCase] hands back the optimistic preview immediately, decoupled from
     * whatever its own submission to the server-authoritative `submitStudySession` Cloud Function
     * returns — that call's own outcome carries no further authority here and is never
     * inspected (see that use case's own KDoc). Only a failed local read behind the preview itself —
     * this account's prior card progress or scoring state — surfaces [StudySessionSummaryMessage.SaveFailed]
     * and leaves [state]'s XP fields at their zero defaults; the counts derived from [SessionResult]
     * itself are untouched either way. [state]'s non-XP fields (mode, duration, counts, …) are already
     * set synchronously in `init`, above, and are not touched again here.
     */
    private fun submitSession() {
        viewModelScope.launch {
            val dailyGoalMinutes = observeUserPreferences().first().dailyGoalMinutes
            val result = route.toSessionResult(dailyGoalMinutes = dailyGoalMinutes)

            submitStudySession(result) { previewResult ->
                previewResult
                    .onSuccess { xpResult -> applyXpResult(result, xpResult) }
                    .onFailure { onPreviewFailed() }
            }
        }
    }

    private fun applyXpResult(result: SessionResult, xpResult: SessionXpResult) {
        val config = result.xpConfig
        _state.update {
            it.copy(
                xpLines = buildXpBreakdownLines(result, xpResult),
                isLoading = false,
                xpTotal = xpResult.breakdown.xpTotal,
                level = xpResult.newScoringState.level,
                xpIntoCurrentLevel = xpResult.newScoringState.xpIntoCurrentLevel,
                xpForNextLevel = config.levelThreshold(xpResult.newScoringState.level),
            )
        }
    }

    private fun onPreviewFailed() {
        _state.update { it.copy(isLoading = false) }
        _messages.tryEmit(StudySessionSummaryMessage.SaveFailed)
    }
}

private const val SECONDS_PER_MINUTE = 60

/**
 * The plain itemised breakdown: one [XpBreakdownLine] per source [xpResult]
 * actually awarded XP for, in the same order as the awards table, zero-[XpBreakdownLine.amount] sources
 * dropped entirely. [SessionXpResult.newCardsStudied], [SessionResult.Rated.partialCount] and counts
 * derived from `cardResults` here (mirroring [CalculateSessionXpUseCase][com.rossomak.flashcards.core.domain.usecase.CalculateSessionXpUseCase]'s
 * own split between a fresh mastery and a defended one) supply each line's [XpBreakdownLine.count];
 * [XpConfig][com.rossomak.flashcards.core.domain.model.XpConfig]'s rates and [xpResult]'s
 * already-multiplied totals supply the rest — nothing here recomputes an amount.
 */
private fun buildXpBreakdownLines(result: SessionResult, xpResult: SessionXpResult): List<XpBreakdownLine> {
    val config = result.xpConfig
    val minutesStudied = result.durationSeconds / SECONDS_PER_MINUTE
    val lines = mutableListOf(
        XpBreakdownLine(XpAwardSource.NewCards, xpResult.newCardsStudied, config.newCardStudied, xpResult.breakdown.newCards),
    )
    if (result is SessionResult.Rated) {
        // Defended (Mastered again after already being Mastered) earns masteryDefenseBonus instead
        // of mastered, not in addition — so newlyMasteredCount, not result.masteredCount, is what
        // count × rate must reproduce mastered's amount.
        val newlyMasteredCount = result.cardResults.count { it.state == FlashcardStudyProgressState.Mastered && !it.wasPreviouslyMastered }
        val defendedCount = result.cardResults.count { it.state == FlashcardStudyProgressState.Mastered && it.wasPreviouslyMastered }
        val demasteredCount = result.cardResults.count { it.state == FlashcardStudyProgressState.Failed && it.wasPreviouslyMastered }
        lines += XpBreakdownLine(XpAwardSource.Mastered, newlyMasteredCount, config.cardMastered, xpResult.breakdown.mastered)
        lines += XpBreakdownLine(XpAwardSource.Partial, result.partialCount, config.cardPartial, xpResult.breakdown.partial)
        lines += XpBreakdownLine(XpAwardSource.MasteryDefended, defendedCount, config.masteryDefended, xpResult.breakdown.masteryDefenseBonus)
        lines += XpBreakdownLine(XpAwardSource.MasteryLost, demasteredCount, config.cardDemastered, xpResult.breakdown.demastered)
    }
    lines += XpBreakdownLine(XpAwardSource.TimeStudied, minutesStudied, config.minuteStudied, xpResult.breakdown.timeStudied)
    lines += XpBreakdownLine(
        XpAwardSource.SessionCompleted,
        count = if (result.abandoned) 0 else 1,
        rate = config.sessionCompleted,
        amount = xpResult.breakdown.sessionCompletionBonus,
    )
    return lines.filter { it.amount != 0 }
}
