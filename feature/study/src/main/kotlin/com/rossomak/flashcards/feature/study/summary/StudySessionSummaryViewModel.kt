package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionResult.Rated
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.LocalPreview
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.ServerScored
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.XpBreakdown
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
     * [state] keeps `isLoading` until [SubmitStudySessionUseCase] returns, which it does exactly once:
     * with the server's score ([ServerScored]) when it arrives in time, otherwise with the local preview
     * ([LocalPreview]). Only a failed local read behind that preview — this account's prior card
     * progress or scoring state — surfaces [StudySessionSummaryMessage.SaveFailed] and leaves [state]'s
     * XP fields at their zero defaults. [state]'s non-XP fields (mode, duration, counts, …) are already
     * set synchronously in `init`, above, and are not touched again here.
     */
    private fun submitSession() {
        viewModelScope.launch {
            val dailyGoalMinutes = observeUserPreferences().first().dailyGoalMinutes
            val result = route.toSessionResult(dailyGoalMinutes = dailyGoalMinutes)

            submitStudySession(result)
                .onSuccess { submissionResult -> applyScore(result, submissionResult.score) }
                .onFailure { onPreviewFailed() }
        }
    }

    /**
     * Every line's count, rate and amount come from [score], whether the server or the local preview
     * scored it. A server answer missing its counts or its rates shows each line's amount only: no
     * client-side value is mixed into the server's lines.
     */
    private fun applyScore(result: SessionResult, score: SessionScore) {
        _state.update {
            it.copy(
                xpLines = buildXpBreakdownLines(result, score.breakdown, score.counts, score.rates),
                isLoading = false,
                xpTotal = score.breakdown.xpTotal,
                level = score.level,
                xpIntoCurrentLevel = score.xpIntoCurrentLevel,
                xpForNextLevel = score.xpForNextLevel,
                levelsCrossed = score.levelsCrossed,
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
 * The plain itemised breakdown: one [XpBreakdownLine] per source [breakdown] actually awarded XP for,
 * in the same order as [XpBreakdown]'s fields, zero-[XpBreakdownLine.amount] sources dropped entirely.
 * [counts] and [rates] supply each multiplied line's [XpBreakdownLine.count] and
 * [XpBreakdownLine.rate]; [breakdown]'s already-multiplied totals supply the amounts — nothing here
 * recomputes an amount. When [counts] or [rates] is `null`, every multiplied line carries its amount
 * only. The Daily Goal and Streak lines are not multiplied per item, so they always carry their amount
 * only.
 */
private fun buildXpBreakdownLines(
    result: SessionResult,
    breakdown: XpBreakdown,
    counts: SessionScoreCounts?,
    rates: SessionScoreRates?,
): List<XpBreakdownLine> {
    val minutesStudied = result.durationSeconds / SECONDS_PER_MINUTE
    val lines = mutableListOf<XpBreakdownLine>()
    fun addMultiplied(source: XpAwardSource, amount: Int, count: (SessionScoreCounts) -> Int?, rate: (SessionScoreRates) -> Int) {
        lines += if (counts == null || rates == null) {
            XpBreakdownLine(source, count = null, rate = null, amount = amount)
        } else {
            XpBreakdownLine(source, count = count(counts) ?: 0, rate = rate(rates), amount = amount)
        }
    }
    addMultiplied(XpAwardSource.NewCards, breakdown.newCards, { it.newCardsStudied }, { it.newCardStudied })
    if (result is Rated) {
        addMultiplied(XpAwardSource.Mastered, breakdown.mastered, { it.newlyMastered }, { it.cardMastered })
        addMultiplied(XpAwardSource.Partial, breakdown.partial, { it.partial }, { it.cardPartial })
        addMultiplied(XpAwardSource.MasteryDefended, breakdown.masteryDefenseBonus, { it.defended }, { it.masteryDefended })
        addMultiplied(XpAwardSource.MasteryLost, breakdown.demastered, { it.demastered }, { it.cardDemastered })
    }
    addMultiplied(XpAwardSource.TimeStudied, breakdown.timeStudied, { minutesStudied }, { it.minuteStudied })
    addMultiplied(XpAwardSource.SessionCompleted, breakdown.sessionCompletionBonus, { if (result.abandoned) 0 else 1 }, { it.sessionCompleted })
    lines += XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = breakdown.dailyGoalBonus)
    lines += XpBreakdownLine(XpAwardSource.Streak, count = null, rate = null, amount = breakdown.streakBonus)
    return lines.filter { it.amount != 0 }
}
