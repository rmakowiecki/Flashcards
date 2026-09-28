package com.rossomak.flashcards.feature.study.rated

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot.LoadFailed
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot.Loading
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot.Running
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.MicPermissionRevoked
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.SessionEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerGradingFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerGradingPause
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerSilencePause
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerSilenceSkip
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoicePlaybackUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionSetup
import com.rossomak.flashcards.core.domain.session.RatedStudySessionCoordinator
import com.rossomak.flashcards.core.domain.usecase.ObserveVoiceAnswerLevelUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.composables.voice.stateInVoiceBarsLevels
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.toVoiceSettings
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.toSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The screen of a Rated Study Session
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * dialogs, the mapping of [RatedStudySessionCoordinator]'s snapshot to screen state, messages,
 * navigation and the microphone bar levels. Every session rule — the queue, Ratings, the Voice
 * Answering round, transport commands, the clock and the result — lives in the coordinator
 * ([ADR-0054](../../../../../../../../docs/adr/0054-study-session-rules-in-domain-coordinators.md)).
 */
@HiltViewModel
class RatedStudySessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val submitCurationReport: SubmitCurationReportUseCase,
    private val observeVoiceAnswerLevel: ObserveVoiceAnswerLevelUseCase,
    private val coordinator: RatedStudySessionCoordinator,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<RatedStudySessionRoute>()

    private val _state = MutableStateFlow(
        RatedStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNameById = route.subcategoryIds.zip(route.subcategoryNames).toMap(),
            attemptsLimit = route.ratedAttempts,
            isVoiceMode = route.voiceAnsweringEnabled,
        ),
    )
    val state: StateFlow<RatedStudySessionScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<RatedStudySessionDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<RatedStudySessionMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<RatedStudySessionMessage> = _messages.asSharedFlow()

    /**
     * Live microphone bar levels for the listening indicator. Kept out of [state] so the level
     * stream never recomposes the rest of the screen.
     */
    val voiceBarsLevels: StateFlow<ImmutableList<Float>> =
        flow { emitAll(observeVoiceAnswerLevel()) }.stateInVoiceBarsLevels(viewModelScope)

    private val isExtendedContextDialogOpen: Boolean
        get() = _state.value.activeDialog is CurrentCardExtendedContext

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    // True only when the pause was caused by the dialog intercepting a natural between-card advance.
    // Gates auto-advance on dialog dismiss and changes play-button behavior.
    private var pausedDueToExtendedContext = false
    private var advanceAfterExtendedContextJob: Job? = null
    private var lastPresentedCardId: String? = null

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    // True only when opening voice settings paused an in-progress playback; gates resume on close.
    private var pausedForVoiceSettings = false

    // Session-scoped like the rest of the routed config: a mid-session change updates only this
    // running session unless the user checks "keep as my default" (ADR-0030), so it lives in a
    // plain var rather than being re-read from the controller on every dialog open.
    private var sessionVoiceSettings: SavedVoiceSettings = route.voiceSettings

    init {
        observeSnapshot()
        observeSessionEvents()
        coordinator.start(
            scope = viewModelScope,
            setup = RatedSessionSetup(
                categoryId = route.categoryId,
                categoryName = route.categoryName,
                subcategoryIds = route.subcategoryIds,
                subcategoryNames = route.subcategoryNames,
                cardIds = route.cardIds,
                sessionTitle = route.sessionTitle,
                voiceSettings = route.voiceSettings,
                voiceAnsweringEnabled = route.voiceAnsweringEnabled,
                attemptsLimit = route.ratedAttempts,
                partialRatingCardRequeueingEnabled = route.partialRatingCardRequeueingEnabled,
            ),
        )
    }

    private fun observeSnapshot() {
        viewModelScope.launch {
            coordinator.sessionState.collect { snapshot ->
                _state.update { it.fromSnapshot(snapshot) }
                if (snapshot is Running) holdAdvanceForExtendedContext(snapshot)
            }
        }
    }

    private fun observeSessionEvents() {
        viewModelScope.launch {
            coordinator.events.collect { event -> onSessionEvent(event) }
        }
    }

    private fun onSessionEvent(event: RatedSessionEvent) {
        when (event) {
            VoiceAnswerSilenceSkip -> _messages.tryEmit(RatedStudySessionMessage.VoiceAnswerSilenceSkip)
            VoiceAnswerSilencePause -> _messages.tryEmit(RatedStudySessionMessage.VoiceAnswerSilencePause)
            is VoiceAnswerGradingFailed -> _messages.tryEmit(event.reason.toMessage())
            VoiceAnswerGradingPause -> _messages.tryEmit(RatedStudySessionMessage.VoiceAnswerGradingPause)
            VoiceAnswerCaptureUnavailable -> _messages.tryEmit(RatedStudySessionMessage.VoiceAnswerCaptureUnavailable)
            VoicePlaybackUnavailable -> _messages.tryEmit(RatedStudySessionMessage.VoicePlaybackUnavailable)
            // The session ends after a delay that gives the snackbar time to show.
            MicPermissionRevoked -> {
                _messages.tryEmit(RatedStudySessionMessage.VoiceAnswerMicPermissionRevoked)
                viewModelScope.launch {
                    delay(MIC_PERMISSION_REVOKED_TERMINATION_DELAY_MS.milliseconds)
                    coordinator.end(abandoned = true)
                }
            }
            is SessionEnded -> viewModelScope.launch {
                eventChannel.send(RatedStudySessionDestination.Summary(event.result.toSummaryRoute()))
            }
        }
    }

    /**
     * A command changes the coordinator's snapshot at once; showing it here, rather than waiting for
     * the snapshot collector to run, keeps the screen in step with the tap that caused it.
     */
    private fun showSessionNow() {
        _state.update { it.fromSnapshot(coordinator.sessionState.value) }
    }

    private fun GradingFailureReason.toMessage(): RatedStudySessionMessage = when (this) {
        GradingFailureReason.NoConnection -> RatedStudySessionMessage.VoiceAnswerGradingOffline
        GradingFailureReason.ServiceError -> RatedStudySessionMessage.VoiceAnswerGradingServiceError
    }

    private fun RatedStudySessionScreenState.fromSnapshot(snapshot: RatedSessionStateSnapshot) = when (snapshot) {
        Loading -> copy(isLoading = true, error = null)
        LoadFailed -> copy(isLoading = false, error = R.string.study_session_load_error_message)
        is Running -> fromRunningSnapshot(snapshot)
    }

    private fun RatedStudySessionScreenState.fromRunningSnapshot(snapshot: Running) = copy(
        isLoading = false,
        error = null,
        flashcards = snapshot.cards,
        // The presented card is always the queue's head.
        currentCardIndex = 0,
        isAnswerRevealed = snapshot.isAnswerRevealed,
        isVoiceActive = snapshot.playback.isActive,
        isVoicePlaying = snapshot.playback.isPlaying,
        speechRate = snapshot.playback.speechRate,
        isVoiceAnswerEnabled = snapshot.isVoiceAnsweringActive,
        voiceAnswerPhase = snapshot.round.phase,
        voiceAnswerSanitizedTranscript = snapshot.round.transcript,
        lastVoiceAnswerGrade = snapshot.round.grade,
        isVoiceShortNoticeSpeaking = snapshot.isShortNoticeSpeaking,
        isVoiceAnswerGradingFailed = snapshot.round.gradingFailure != null,
        masteredCount = snapshot.masteredCount,
        completedCount = snapshot.completedCount,
        distinctCardCount = snapshot.distinctCardCount,
        currentCardRatings = snapshot.currentCardRatings,
        isVoiceAnswerPaused = snapshot.voiceAnswerPauseReason != null || snapshot.pauseReason != null,
        isVoiceEngineUnavailable = snapshot.pauseReason == SessionPauseReason.VoiceEngineUnavailable,
    )

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    private fun holdAdvanceForExtendedContext(snapshot: Running) {
        val playback = snapshot.playback
        val presentedCardId = snapshot.cards.firstOrNull()?.id
        if (!playback.isActive || presentedCardId != lastPresentedCardId) {
            lastPresentedCardId = presentedCardId
            advanceAfterExtendedContextJob?.cancel()
            pausedDueToExtendedContext = false
        }
        if (playback.isInBetweenPause && playback.isPlaying && isExtendedContextDialogOpen && !pausedDueToExtendedContext) {
            pausedDueToExtendedContext = true
            coordinator.pause()
        }
    }

    /** Resumes a paused session: voice answering, or the whole voice stack after an engine failure. */
    fun onResumeSession() {
        coordinator.resume()
    }

    fun onShowAnswer() {
        coordinator.revealAnswer()
        showSessionNow()
    }

    /**
     * A manual self-rating of the presented card. A voice grade applies its Rating inside the
     * coordinator, the same way.
     */
    fun onAttemptRating(rating: FlashcardAttemptRating) {
        coordinator.rate(rating)
        showSessionNow()
    }

    fun onVoicePlayPause() {
        when {
            _state.value.isVoiceAnswerPaused -> onResumeSession()
            pausedDueToExtendedContext -> {
                advanceAfterExtendedContextJob?.cancel()
                pausedDueToExtendedContext = false
                coordinator.next()
                coordinator.play()
            }
            _state.value.isVoicePlaying -> coordinator.pause()
            else -> coordinator.play()
        }
        showSessionNow()
    }

    fun onVoiceNext() {
        advanceAfterExtendedContextJob?.cancel()
        pausedDueToExtendedContext = false
        coordinator.next()
        showSessionNow()
    }

    fun onVoicePrevious() {
        advanceAfterExtendedContextJob?.cancel()
        pausedDueToExtendedContext = false
        coordinator.previous()
        showSessionNow()
    }

    fun onVoiceSpeedChange(rate: Float) {
        coordinator.setSpeechRate(rate)
    }

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    private fun onExtendedContextDialogOpen(dialog: CurrentCardExtendedContext) {
        _state.update { it.copy(activeDialog = dialog) }
        val playback = (coordinator.sessionState.value as? Running)?.playback ?: return
        if (playback.isInBetweenPause && playback.isPlaying) {
            pausedDueToExtendedContext = true
            coordinator.pause()
        }
    }

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    private fun onExtendedContextDialogDismissed() {
        if (pausedDueToExtendedContext) {
            advanceAfterExtendedContextJob = viewModelScope.launch {
                delay(EXTENDED_CONTEXT_ADVANCE_DELAY_MS.milliseconds)
                pausedDueToExtendedContext = false
                coordinator.next()
                coordinator.play()
            }
        }
    }

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    private fun onVoiceSettingsOpen() {
        if (_state.value.isVoicePlaying) {
            pausedForVoiceSettings = true
            coordinator.pause()
        }
        _state.update {
            it.copy(activeDialog = SessionVoiceSettings(voiceSettingsController.seedDraft(sessionVoiceSettings)))
        }
        voiceSettingsController.loadVoices(viewModelScope, ::onVoicesLoaded)
    }

    /**
     * The voice list arrives after the dialog is already up, so it has to find the open dialog to
     * fill in — the one narrowing cast left in the dialog path, once per open rather than once per
     * edit. A dismissal in the meantime correctly drops it.
     */
    private fun onVoicesLoaded(voices: List<VoiceOption>) {
        _state.update { state ->
            val dialog = state.activeDialog as? SessionVoiceSettings ?: return@update state
            state.copy(
                activeDialog = dialog.copy(
                    draftState = dialog.draftState.copy(
                        availableVoices = voices,
                        draftVoiceId = dialog.draftState.draftVoiceId ?: voices.firstOrNull()?.id,
                    ),
                ),
            )
        }
    }

    /**
     * Always applies to the rest of this session; only persists as the new default when the
     * dialog's checkbox is checked (ADR-0030). Either way the preview player is done with — a save
     * stops it same as [voiceSettingsController]'s own `save` would, and an unchecked confirm has
     * no other call into the controller left to do that.
     */
    private fun onVoiceSettingsSave() {
        val dialog = _state.value.activeDialog as? SessionVoiceSettings ?: return
        val settings = dialog.draftState.toVoiceSettings()
        sessionVoiceSettings = settings
        if (dialog.keepAsDefault) {
            voiceSettingsController.save(viewModelScope, dialog.draftState)
        } else {
            voiceSettingsController.stopPreview()
        }
        coordinator.applyVoiceSettings(settings)
        _state.update { it.copy(activeDialog = null) }
        resumeIfPausedForVoiceSettings()
    }

    private fun onVoiceSettingsDismiss() {
        voiceSettingsController.stopPreview()
        _state.update { it.copy(activeDialog = null) }
        resumeIfPausedForVoiceSettings()
    }

    private fun resumeIfPausedForVoiceSettings() {
        if (pausedForVoiceSettings) {
            pausedForVoiceSettings = false
            coordinator.play()
        }
    }

    /**
     * Single entry point for every dialog on this screen. Exit-session confirmation is the one
     * case with no ViewModel work behind it — the screen navigates and there is nothing to commit.
     */
    fun onDialogEvent(event: StudySessionDialogEvent) {
        when (event) {
            is Open -> onDialogOpen(event.dialog)
            is DraftChange -> onDraftChange(event.dialog)
            Confirm -> onDialogConfirm()
            Dismiss -> onDialogDismiss()
        }
    }

    /**
     * The caller hands over the dialog it wants shown, already seeded from what it was rendering.
     * This adds only what the call site could not: the playback side effects, and the voice-settings
     * draftState, which comes from the shared controller rather than screen state.
     */
    private fun onDialogOpen(dialog: StudySessionDialog) {
        when (dialog) {
            is ReportCurrentCardProblem -> onReportProblemOpen(dialog)
            is CurrentCardExtendedContext -> onExtendedContextDialogOpen(dialog)
            is SessionVoiceSettings -> onVoiceSettingsOpen()
            ExitSession -> _state.update { it.copy(activeDialog = dialog) }
        }
    }

    private fun onDraftChange(dialog: StudySessionDialog) {
        val previous = _state.value.activeDialog
        _state.update { it.copy(activeDialog = dialog) }
        if (previous is SessionVoiceSettings &&
            dialog is SessionVoiceSettings &&
            dialog.draftState != previous.draftState
        ) {
            voiceSettingsController.preview(dialog.draftState)
        }
    }

    private fun onDialogConfirm() {
        when (_state.value.activeDialog) {
            is ReportCurrentCardProblem -> onReportProblemSubmit()
            is SessionVoiceSettings -> onVoiceSettingsSave()
            ExitSession -> {
                onDialogDismiss()
                coordinator.end(abandoned = true)
            }
            // "Got it" and a scrim tap are the same act on a single-action dialog.
            is CurrentCardExtendedContext, null -> onDialogDismiss()
        }
    }

    /** Always the discard path: the draftState dies with the field. */
    private fun onDialogDismiss() {
        val dialog = _state.value.activeDialog
        _state.update { it.copy(activeDialog = null) }
        when (dialog) {
            is CurrentCardExtendedContext -> onExtendedContextDialogDismissed()
            is SessionVoiceSettings -> onVoiceSettingsDismiss()
            else -> Unit
        }
    }

    private fun onReportProblemOpen(dialog: ReportCurrentCardProblem) {
        if (_state.value.isVoicePlaying) coordinator.pause()
        _state.update { it.copy(activeDialog = dialog) }
    }

    private fun onReportProblemSubmit() {
        val dialog = _state.value.activeDialog as? ReportCurrentCardProblem ?: return
        if (!dialog.canSubmit) return
        _state.update { it.copy(activeDialog = null) }
        viewModelScope.launch {
            submitCurationReport(
                SubmitCurationReportUseCase.Params(
                    cardId = dialog.cardId,
                    subcategoryId = dialog.subcategoryId,
                    actions = dialog.selectedActions,
                )
            ).onFailure {
                _messages.tryEmit(RatedStudySessionMessage.CurationSubmissionFailed)
            }
        }
    }

    public override fun onCleared() {
        coordinator.stop()
    }

    private companion object {
        const val EXTENDED_CONTEXT_ADVANCE_DELAY_MS = 500L
        const val MIC_PERMISSION_REVOKED_TERMINATION_DELAY_MS = 4000L
    }
}
