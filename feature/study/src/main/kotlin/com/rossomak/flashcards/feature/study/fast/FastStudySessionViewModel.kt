package com.rossomak.flashcards.feature.study.fast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.LoadFailed
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.Loading
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.Running
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.session.FastSessionEvent
import com.rossomak.flashcards.core.domain.session.FastSessionSetup
import com.rossomak.flashcards.core.domain.session.FastStudySessionCoordinator
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.toVoiceSettings
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.R
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The screen of a Fast Study Session
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * dialogs, the mapping of [FastStudySessionCoordinator]'s snapshot to screen state, messages and
 * navigation. Every session rule — the Studied set, read-aloud, transport commands, the clock and
 * the result — lives in the coordinator
 * ([ADR-0054](../../../../../../../../docs/adr/0054-study-session-rules-in-domain-coordinators.md)).
 */
@HiltViewModel
class FastStudySessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val submitCurationReport: SubmitCurationReportUseCase,
    private val coordinator: FastStudySessionCoordinator,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<FastStudySessionRoute>()

    private val _state = MutableStateFlow(
        FastStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNameById = route.subcategoryIds.zip(route.subcategoryNames).toMap(),
            isReadAloudMode = route.readAloudEnabled,
        ),
    )
    val state: StateFlow<FastStudySessionScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<FastStudySessionDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<FastStudySessionMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<FastStudySessionMessage> = _messages.asSharedFlow()

    private val isExtendedContextDialogOpen: Boolean
        get() = _state.value.activeDialog is CurrentCardExtendedContext

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    // True only when the pause was caused by the dialog intercepting a natural between-card advance.
    // Gates auto-advance on dialog dismiss and changes play-button behavior.
    private var pausedDueToExtendedContext = false
    private var advanceAfterExtendedContextJob: Job? = null
    private var lastObservedCardIndex = NO_CARD_INDEX

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
            setup = FastSessionSetup(
                categoryId = route.categoryId,
                categoryName = route.categoryName,
                subcategoryIds = route.subcategoryIds,
                subcategoryNames = route.subcategoryNames,
                cardIds = route.cardIds,
                sessionTitle = route.sessionTitle,
                voiceSettings = route.voiceSettings,
                readAloudEnabled = route.readAloudEnabled,
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
            coordinator.events.collect { event ->
                when (event) {
                    FastSessionEvent.VoicePlaybackUnavailable -> _messages.tryEmit(FastStudySessionMessage.VoicePlaybackUnavailable)
                    is FastSessionEvent.SessionEnded -> eventChannel.send(FastStudySessionDestination.Summary(event.result.toSummaryRoute()))
                }
            }
        }
    }

    private fun FastStudySessionScreenState.fromSnapshot(snapshot: FastSessionStateSnapshot) = when (snapshot) {
        Loading -> copy(isLoading = true, error = null)
        LoadFailed -> copy(isLoading = false, error = R.string.study_session_load_error_message)
        is Running -> copy(
            isLoading = false,
            error = null,
            flashcards = snapshot.cards,
            currentCardIndex = snapshot.currentIndex,
            isAnswerRevealed = snapshot.isAnswerRevealed,
            isVoiceActive = snapshot.playback.isActive,
            isVoicePlaying = snapshot.playback.isPlaying,
            isReadAloudNextAvailable = snapshot.isReadAloudNextAvailable,
            speechRate = snapshot.playback.speechRate,
            isVoiceEngineUnavailable = snapshot.pauseReason == SessionPauseReason.VoiceEngineUnavailable,
        )
    }

    /**
     * A command changes the coordinator's snapshot at once; showing it here, rather than waiting for
     * the snapshot collector to run, keeps the screen in step with the tap that caused it.
     */
    private fun showSessionNow() {
        _state.update { it.fromSnapshot(coordinator.sessionState.value) }
    }

    // Replaced by the coordinator's own advance hold once dialogs hold at the advance point.
    private fun holdAdvanceForExtendedContext(snapshot: Running) {
        val playback = snapshot.playback
        if (!playback.isActive || playback.currentIndex != lastObservedCardIndex) {
            lastObservedCardIndex = if (playback.isActive) playback.currentIndex else NO_CARD_INDEX
            advanceAfterExtendedContextJob?.cancel()
            pausedDueToExtendedContext = false
        }
        if (playback.isInBetweenPause && playback.isPlaying && isExtendedContextDialogOpen && !pausedDueToExtendedContext) {
            pausedDueToExtendedContext = true
            coordinator.pause()
        }
    }

    fun onShowAnswer() {
        coordinator.revealAnswer()
        showSessionNow()
    }

    /**
     * The Fast sheet's manual advance affordance — the only way to move on without Read-aloud. Only
     * reachable once the current card's answer is revealed (the sheet shows "Show answer" until
     * then), so the last card is always fully Studied before this can end the session.
     */
    fun onNextCard() {
        coordinator.nextCard()
        showSessionNow()
    }

    fun onVoicePlayPause() {
        when {
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
            // "Got it" and a scrim tap on the single-action Extended Context dialog are the same act.
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

    /**
     * Reporting pauses playback the way the old debug FAB did — the user stopped to read the card,
     * not to be read over. Resuming is a deliberate tap (ADR-0017).
     */
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
                _messages.tryEmit(FastStudySessionMessage.CurationReportFailed)
            }
        }
    }

    public override fun onCleared() {
        coordinator.stop()
    }

    private companion object {
        const val EXTENDED_CONTEXT_ADVANCE_DELAY_MS = 500L
        const val NO_CARD_INDEX = -1
    }
}
