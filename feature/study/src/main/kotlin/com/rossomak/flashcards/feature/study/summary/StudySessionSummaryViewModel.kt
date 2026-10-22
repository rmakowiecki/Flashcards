package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionNotSavedException
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionReplay
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveScoringStateUseCase
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
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import com.rossomak.flashcards.feature.study.toSessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Submits the session from the route once, from `init`. The resolved score is saved in [savedStateHandle],
 * so a restore after process death shows it without submitting again.
 */
@HiltViewModel
class StudySessionSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val submitStudySession: SubmitStudySessionUseCase,
    private val observeAuthUser: ObserveAuthUserUseCase,
    private val observeScoringState: ObserveScoringStateUseCase,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<StudySessionSummaryRoute>()

    private val _state = MutableStateFlow(StudySessionSummaryScreenState())
    val state: StateFlow<StudySessionSummaryScreenState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<StudySessionSummaryMessage>(extraBufferCapacity = 1)

    val messages: SharedFlow<StudySessionSummaryMessage> = _messages.asSharedFlow()

    private val eventChannel = Channel<StudySessionSummaryDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var studyAgainSent = false

    init {
        // Synchronous, so the first observed state already has the route's mode and counts.
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
        observeLevelCard()
    }

    /** A failed read still resolves the auth user, so the Level card shows without a name, like Home's. */
    private fun observeAvatarSource() {
        viewModelScope.launch {
            observeAuthUser()
                .catch { exception ->
                    logw(exception) { "Failed to observe the auth user for the Level card avatar" }
                    _state.update { it.copy(hasAuthUserResolved = true, photoUrl = null, displayName = null) }
                }
                .collect { authUser ->
                    _state.update {
                        it.copy(hasAuthUserResolved = true, photoUrl = authUser?.photoUrl, displayName = authUser?.displayName)
                    }
                }
        }
    }

    /**
     * Only an unscored Summary draws its card from this. Unavailable only if the stream fails or ends before
     * its first value. The use case is called inside `flow` so a failure building it is caught too.
     */
    private fun observeLevelCard() {
        viewModelScope.launch {
            var hasLevelEmitted = false
            flow { emitAll(observeScoringState()) }
                .map { observedScoringState -> observedScoringState.levelProgress }
                .distinctUntilChanged()
                .onEach { hasLevelEmitted = true }
                .onCompletion { cause ->
                    if (cause == null && !hasLevelEmitted) {
                        loge { "Observing the Level for the Summary's Level card completed before its first emission" }
                        setLiveLevelUnavailable()
                    }
                }
                .catch { exception ->
                    loge(exception) { "Observing the Level for the Summary's Level card failed" }
                    if (!hasLevelEmitted) setLiveLevelUnavailable()
                }
                .collect { levelProgress ->
                    _state.update { it.copy(liveLevel = levelProgress) }
                }
        }
    }

    private fun setLiveLevelUnavailable() {
        _state.update { it.copy(isLiveLevelUnavailable = true) }
    }

    /**
     * The Daily Goal is read once here and baked into the result, never re-read at delivery (ADR-0048).
     * A failure is not saved, so a restore submits again.
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
                .onFailure { exception -> onSubmissionFailed(exception) }
        }
    }

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

    private fun onSubmissionFailed(exception: Throwable) {
        _state.update { it.copy(scoreStatus = Unavailable) }
        val message = when (exception) {
            is SessionNotSavedException -> StudySessionSummaryMessage.SaveFailed
            else -> StudySessionSummaryMessage.XpUnavailable
        }
        _messages.tryEmit(message)
    }

    /** Only the first click navigates: the buffered channel would otherwise send a double tap twice. */
    fun onStudyAgainClick() {
        if (studyAgainSent) return
        studyAgainSent = true
        val replay = StudySessionReplay.of(
            categoryId = route.categoryId,
            categoryName = route.categoryName,
            sourceType = route.sourceType,
            subcategoryIds = route.subcategoryIds,
            subcategoryNames = route.subcategoryNames,
            studyMode = route.mode,
            voiceAnsweringEnabled = route.voiceAnsweringEnabled,
            readAloudEnabled = route.readAloudEnabled,
        )
        viewModelScope.launch { eventChannel.send(StudySessionSummaryDestination.StudyAgain(replay)) }
    }

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
        const val KEY_SAVED_SCORE = "savedScore"
    }
}

/** [scoredXpTotal] is `null` until the score exists. */
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

/** Amounts are [score]'s own totals, never recomputed; zero amounts are dropped. */
private fun buildXpBreakdownLines(route: StudySessionSummaryRoute, score: SessionScore): List<XpBreakdownLine> {
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
        lines += XpBreakdownLine(XpAwardSource.Streak, count = currentStreak, rate = null, amount = breakdown.streakBonus)
        lines += XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = breakdown.sessionCompletionBonus)
        lines += XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = breakdown.dailyGoalBonus)
    }
    return lines.filter { it.amount != 0 }
}
