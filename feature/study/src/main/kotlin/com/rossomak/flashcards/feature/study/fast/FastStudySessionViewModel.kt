package com.rossomak.flashcards.feature.study.fast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.LoadFailed
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.Loading
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot.Running
import com.rossomak.flashcards.core.domain.model.TransportCommand
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
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.toSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
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

    // Identifies the report submission in flight, so a result closes only the dialog it was sent from.
    private var reportSubmissionId = 0

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
                sourceType = route.sourceType,
            ),
        )
    }

    private fun observeSnapshot() {
        viewModelScope.launch {
            coordinator.sessionState.collect { snapshot ->
                // Nothing to study: Preview, underneath, owns the load error and its Retry.
                if (snapshot == LoadFailed) eventChannel.send(FastStudySessionDestination.Back) else _state.update { it.fromSnapshot(snapshot) }
            }
        }
    }

    private fun observeSessionEvents() {
        viewModelScope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    FastSessionEvent.VoicePlaybackUnavailable -> _messages.tryEmit(FastStudySessionMessage.VoicePlaybackUnavailable)
                    FastSessionEvent.PlayIgnoredDuringCall -> _messages.tryEmit(FastStudySessionMessage.PlayIgnoredDuringCall)
                    is FastSessionEvent.ExternalTransportCommand -> onExternalTransportCommand(event.command)
                    is FastSessionEvent.SessionEnded -> eventChannel.send(FastStudySessionDestination.Summary(event.result.toSummaryRoute()))
                }
            }
        }
    }

    private fun FastStudySessionScreenState.fromSnapshot(snapshot: FastSessionStateSnapshot) = when (snapshot) {
        Loading -> copy(isLoading = true)
        // Never shown: the collector navigates back instead.
        LoadFailed -> this
        is Running -> copy(
            isLoading = false,
            flashcards = snapshot.cards,
            currentCardIndex = snapshot.currentIndex,
            isAnswerRevealed = snapshot.isAnswerRevealed,
            isVoiceActive = snapshot.playback.isActive,
            isVoicePlaying = snapshot.playback.isPlaying,
            isVoiceEngineUnavailable = snapshot.pauseReason == FastPauseReason.VoiceEngineUnavailable,
            availableTransportCommands = snapshot.availableTransportCommands,
        )
    }

    /**
     * A command changes the coordinator's snapshot at once; showing it here, rather than waiting for
     * the snapshot collector to run, keeps the screen in step with the tap that caused it.
     */
    private fun showSessionNow() {
        _state.update { it.fromSnapshot(coordinator.sessionState.value) }
    }

    /**
     * A command from outside the app that resumed playback or changed the card dismisses the open
     * dialog, dropping its draft. A pause keeps it open.
     */
    private fun onExternalTransportCommand(command: TransportCommand) {
        val isPause = command == TransportCommand.Pause || command == TransportCommand.Stop
        if (!isPause && _state.value.activeDialog != null) onDialogDismiss()
        showSessionNow()
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
        coordinator.next()
        showSessionNow()
    }

    fun onVoicePlayPause() {
        if (_state.value.isVoicePlaying) coordinator.pause() else coordinator.play()
        showSessionNow()
    }

    fun onVoiceNext() {
        coordinator.next()
        showSessionNow()
    }

    fun onVoicePrevious() {
        coordinator.previous()
        showSessionNow()
    }

    /** The one dialog that pauses: voice settings are previewed aloud, which would talk over the session. */
    private fun onVoiceSettingsOpen() {
        coordinator.pauseTemporarily()
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
        closeDialog()
    }

    /**
     * Single entry point for every dialog on this screen. No dialog pauses the session except voice
     * settings; while one is open the session holds at its auto-advance point instead.
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
        coordinator.holdAdvance()
        when (dialog) {
            is SessionVoiceSettings -> onVoiceSettingsOpen()
            is ReportCurrentCardProblem, is CurrentCardExtendedContext, ExitSession -> _state.update { it.copy(activeDialog = dialog) }
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
            // The session ends here, so the hold is never released: releasing it could still move on.
            ExitSession -> {
                _state.update { it.copy(activeDialog = null) }
                coordinator.end(abandoned = true)
            }
            // "Got it" and a scrim tap on the single-action Extended Context dialog are the same act.
            is CurrentCardExtendedContext, null -> onDialogDismiss()
        }
    }

    /** Always the discard path: the draftState dies with the field. */
    private fun onDialogDismiss() {
        if (_state.value.activeDialog is SessionVoiceSettings) voiceSettingsController.stopPreview()
        closeDialog()
    }

    /** A held session moves on once the old card has lingered; voice settings end their pause first. */
    private fun closeDialog() {
        val dialog = _state.value.activeDialog
        _state.update { it.copy(activeDialog = null) }
        if (dialog is SessionVoiceSettings) coordinator.endTemporaryPause()
        coordinator.releaseAdvance()
        showSessionNow()
    }

    /**
     * The dialog stays open until the result, with Submit disabled meanwhile. A failure keeps it open
     * and releases nothing; a dismissal meanwhile is an ordinary close, and a later failure still
     * shows its message.
     */
    private fun onReportProblemSubmit() {
        val dialog = _state.value.activeDialog as? ReportCurrentCardProblem ?: return
        if (!dialog.canSubmit) return
        val submissionId = ++reportSubmissionId
        _state.update { it.copy(activeDialog = dialog.copy(isSubmitting = true)) }
        viewModelScope.launch {
            val result = submitCurationReport(
                SubmitCurationReportUseCase.Params(
                    cardId = dialog.cardId,
                    subcategoryId = dialog.subcategoryId,
                    actions = dialog.selectedActions,
                )
            )
            val submittingDialog = (_state.value.activeDialog as? ReportCurrentCardProblem)
                ?.takeIf { it.isSubmitting && submissionId == reportSubmissionId }
            result
                .onSuccess { if (submittingDialog != null) closeDialog() }
                .onFailure {
                    if (submittingDialog != null) _state.update { it.copy(activeDialog = submittingDialog.copy(isSubmitting = false)) }
                    _messages.tryEmit(FastStudySessionMessage.CurationReportFailed)
                }
        }
    }

    public override fun onCleared() {
        coordinator.stop()
    }
}
