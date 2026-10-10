package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.LocalPreview
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.ServerScored
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.GreatWork
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.NiceEffort
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.PerfectRun
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import com.rossomak.flashcards.feature.study.toSessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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
 *
 * A resolved score is saved in [savedStateHandle], so a ViewModel restored after process death shows
 * the same award without submitting again. Only a death before the score resolved submits again; the
 * use case's baseline then leaves this session out, so the preview never builds on the session itself.
 */
@HiltViewModel
class StudySessionSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val submitStudySession: SubmitStudySessionUseCase,
    private val observeAuthUser: ObserveAuthUserUseCase,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<StudySessionSummaryRoute>()

    private val _state = MutableStateFlow(StudySessionSummaryScreenState())
    val state: StateFlow<StudySessionSummaryScreenState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<StudySessionSummaryMessage>(extraBufferCapacity = 1)

    val messages: SharedFlow<StudySessionSummaryMessage> = _messages.asSharedFlow()

    init {
        // Written directly in init, not inside submitSession()'s coroutine: this must land before
        // the ViewModel instance is handed back to hiltViewModel(), so the very first state Compose
        // ever observes already carries the real mode and counts, never the
        // StudySessionSummaryScreenState default. They all sit on `route` already, with no
        // I/O dependency, so there is no reason to make them wait behind submitSession()'s async read.
        //
        // masteredCount is 0 for a Fast result, which has no Terminal States (see
        // StudySessionSummaryScreenState's own KDoc): the screen chooses its layout off `mode`, never
        // off this being zero.
        val masteredCount = when (route.mode) {
            StudyMode.Rated -> route.cardStates.count { it == FlashcardStudyProgressState.Mastered }
            StudyMode.Fast -> 0
        }
        _state.update {
            it.copy(
                mode = route.mode,
                durationSeconds = route.durationSeconds,
                studiedCount = route.cardIds.size,
                abandoned = route.abandoned,
                masteredCount = masteredCount,
                headline = resolveHeadline(route.mode, route.abandoned, masteredCount, route.cardIds.size, scoredXpTotal = null),
            )
        }
        val savedScore = savedStateHandle.get<String>(KEY_SAVED_SCORE)?.let { json -> Json.decodeFromString<SessionScore>(json) }
        if (savedScore != null) applyScore(savedScore) else submitSession()
        observeAvatarSource()
    }

    /**
     * Mirrors the signed-in User's photo and name into [state] for the Level card's avatar. Runs in
     * its own coroutine so it neither delays nor reorders the synchronous `init` writes or the
     * submission; a `null` emission (no signed-in User) clears both fields. A failed read just
     * leaves the avatar on its fallback.
     */
    private fun observeAvatarSource() {
        viewModelScope.launch {
            observeAuthUser()
                .catch { exception -> logw(exception) { "Failed to observe the auth user for the Level card avatar" } }
                .collect { authUser ->
                    _state.update { it.copy(photoUrl = authUser?.photoUrl, displayName = authUser?.displayName) }
                }
        }
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
     * [state] keeps [Loading] until [SubmitStudySessionUseCase] returns, which it does exactly once:
     * with the server's score ([ServerScored]) when it arrives in time, otherwise with the local preview
     * ([LocalPreview]). Only a failed local read behind that preview, of this account's prior card
     * progress or scoring state, makes it [Unavailable], surfaces [StudySessionSummaryMessage.XpUnavailable]
     * and leaves [state]'s XP fields at their zero defaults. A resolved score is saved for a restore; a
     * failed preview is not, so a restore tries again. [state]'s non-XP fields (mode, duration, counts, …)
     * are already set synchronously in `init`, above, and are not touched again here.
     */
    private fun submitSession() {
        viewModelScope.launch {
            val dailyGoalMinutes = observeUserPreferences().first().dailyGoalMinutes
            val result = route.toSessionResult(dailyGoalMinutes = dailyGoalMinutes)

            submitStudySession(result)
                .onSuccess { submissionResult ->
                    savedStateHandle[KEY_SAVED_SCORE] = Json.encodeToString(submissionResult.score)
                    applyScore(submissionResult.score)
                }
                .onFailure { onPreviewFailed() }
        }
    }

    /**
     * Every line's count, rate and amount, and the Level position before and after the session, come
     * from [score], whether the server or the local preview scored it: no client-side value is mixed
     * into the server's lines.
     */
    private fun applyScore(score: SessionScore) {
        _state.update {
            it.copy(
                xpLines = buildXpBreakdownLines(route, score),
                scoreStatus = Scored,
                headline = resolveHeadline(it.mode, it.abandoned, it.masteredCount, it.studiedCount, scoredXpTotal = score.breakdown.xpTotal),
                xpTotal = score.breakdown.xpTotal,
                level = score.level,
                xpIntoCurrentLevel = score.xpIntoCurrentLevel,
                xpForNextLevel = score.xpForNextLevel,
                levelsCrossed = score.levelsCrossed,
                levelBefore = score.levelBefore,
                xpIntoCurrentLevelBefore = score.xpIntoCurrentLevelBefore,
                xpForNextLevelBefore = score.xpForNextLevelBefore,
                currentStreak = score.currentStreak,
            )
        }
    }

    private fun onPreviewFailed() {
        _state.update { it.copy(scoreStatus = Unavailable) }
        _messages.tryEmit(StudySessionSummaryMessage.XpUnavailable)
    }

    /** Single entry point for every dialog on this screen. */
    fun onDialogEvent(event: StudySessionSummaryDialogEvent) {
        when (event) {
            // The breakdown only exists once there is a score to itemise.
            is Open -> if (_state.value.scoreStatus == Scored) _state.update { it.copy(activeDialog = event.dialog) }
            // Nothing in a read-only dialog is editable.
            is DraftChange -> Unit
            Confirm, Dismiss -> _state.update { it.copy(activeDialog = null) }
        }
    }

    private companion object {
        /** The resolved [SessionScore] as JSON, so a restored ViewModel shows it without submitting again. */
        const val KEY_SAVED_SCORE = "savedScore"
    }
}

private const val SECONDS_PER_MINUTE = 60

/**
 * Abandoned sessions and sessions that lost XP read as [NiceEffort], whatever else is true; a Rated
 * session with every studied card Mastered (defended cards included) is a [PerfectRun]. A negative
 * total can only be known once the score exists, so [scoredXpTotal] is `null` before then.
 */
private fun resolveHeadline(
    mode: StudyMode,
    abandoned: Boolean,
    masteredCount: Int,
    studiedCount: Int,
    scoredXpTotal: Int?,
): StudySessionSummaryHeadline = when {
    abandoned || (scoredXpTotal != null && scoredXpTotal < 0) -> NiceEffort
    mode == StudyMode.Rated && masteredCount == studiedCount -> PerfectRun
    else -> GreatWork
}

/**
 * The itemised breakdown: one [XpBreakdownLine] per source [score] actually awarded XP for, in the
 * order the breakdown dialog shows them, zero-[XpBreakdownLine.amount] sources dropped entirely.
 * [score]'s counts and rates supply each multiplied line's [XpBreakdownLine.count] and
 * [XpBreakdownLine.rate], and its already-multiplied totals supply the amounts: nothing here
 * recomputes an amount. A Fast session never has the Rated-only lines.
 */
private fun buildXpBreakdownLines(route: StudySessionSummaryRoute, score: SessionScore): List<XpBreakdownLine> {
    val minutesStudied = route.durationSeconds / SECONDS_PER_MINUTE
    val lines = mutableListOf<XpBreakdownLine>()
    fun addMultiplied(source: XpAwardSource, amount: Int, count: Int, rate: Int) {
        lines += XpBreakdownLine(source, count = count, rate = rate, amount = amount)
    }
    with(score) {
        addMultiplied(XpAwardSource.NewCards, breakdown.newCards, counts.newCardsStudied, rates.newCardStudied)
        if (route.mode == StudyMode.Rated) {
            addMultiplied(XpAwardSource.Mastered, breakdown.mastered, counts.newlyMastered ?: 0, rates.cardMastered)
            addMultiplied(XpAwardSource.Partial, breakdown.partial, counts.partial ?: 0, rates.cardPartial)
            addMultiplied(XpAwardSource.MasteryDefended, breakdown.masteryDefenseBonus, counts.defended ?: 0, rates.masteryDefended)
            addMultiplied(XpAwardSource.MasteryLost, breakdown.demastered, counts.demastered ?: 0, rates.cardDemastered)
        }
        addMultiplied(XpAwardSource.TimeStudied, breakdown.timeStudied, minutesStudied, rates.minuteStudied)
        lines += XpBreakdownLine(XpAwardSource.Streak, count = currentStreak, rate = null, amount = breakdown.streakBonus)
        lines += XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = breakdown.sessionCompletionBonus)
        lines += XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = breakdown.dailyGoalBonus)
    }
    return lines.filter { it.amount != 0 }
}
