package com.rossomak.flashcards.feature.study.fast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.annotation.ArchConventionExempt
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionClock
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.sealSessionResult
import com.rossomak.flashcards.core.domain.model.startClock
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
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
import com.rossomak.flashcards.feature.study.voice.VoiceGateway
import com.rossomak.flashcards.feature.study.voice.VoicePhase
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
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
 * Runs a Fast Study Session end to end. Knows nothing about Ratings, Attempts or voice answering —
 * those are Rated concepts
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)).
 *
 * No user-preferences use cases: their only current purpose is the voice-answering consent flag,
 * and voice answering is Rated-only (ADR-0025). Fast has no path to it.
 */
@HiltViewModel
@ArchConventionExempt("Injects VoiceGateway directly, pending a use-case wrap (ADR-0051)")
class FastStudySessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSessionStartData: GetSessionStartDataUseCase,
    private val submitCurationReport: SubmitCurationReportUseCase,
    private val voiceGateway: VoiceGateway,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<FastStudySessionRoute>()

    // Distinct from the screen's own per-card title (chrome.studySessionCardTitle):
    // this is the fixed name the voice gateway's notification shows for the whole session.
    private val sessionTitle: String = route.sessionTitle

    private val _state = MutableStateFlow(
        FastStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNameById = route.subcategoryIds.zip(route.subcategoryNames).toMap(),
            isReadAloudMode = route.readAloudEnabled,
        ),
    )
    val state: StateFlow<FastStudySessionScreenState> = _state.asStateFlow()

    // Tracks eagerly so rapid toggles don't race against isVoiceActive propagation.
    private var voiceStarted = false

    internal var rewindThresholdMs: Long = VoicePlaybackState.REWIND_THRESHOLD_MS

    private var rewindJob: Job? = null
    private var isPastRewindThreshold = false
    private val eventChannel = Channel<FastStudySessionDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<FastStudySessionMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<FastStudySessionMessage> = _messages.asSharedFlow()

    private var lastObservedCardIndex = -1

    private val isExtendedContextDialogOpen: Boolean
        get() = _state.value.activeDialog is CurrentCardExtendedContext

    // True only when the pause was caused by the dialog intercepting a natural between-card advance.
    // Gates auto-advance on dialog dismiss and changes play-button behavior.
    private var pausedDueToExtendedContext = false
    private var advanceAfterExtendedContextJob: Job? = null

    // True only when opening voice settings paused an in-progress playback; gates resume on close.
    private var pausedForVoiceSettings = false

    // Session-scoped like the rest of the routed config: a mid-session change updates only this
    // running session unless the user checks "keep as my default" (ADR-0030), so it lives in a
    // plain var rather than being re-read from the controller on every playback start.
    private var sessionVoiceSettings: SavedVoiceSettings = route.voiceSettings

    // Generated once per session and carried on the ViewModel rather than SavedStateHandle — the
    // ViewModel instance itself already survives rotation, and there is nothing to restore it from
    // after an app kill (no in-progress persistence, by design).
    private val sessionId: String = UUID.randomUUID().toString()

    // Started once, at first card shown, and never paused — v1 is deliberately simplistic: wall
    // time from first card shown to termination, unconditional of backgrounding or playback state.
    // Revisit if a richer policy (e.g. pausing on background) is needed later.
    private var clock: SessionClock = SessionClock()

    // The instant the clock started — carried separately because a session whose card load fails
    // never starts it at all, and SessionResult.startedAt needs that distinction.
    private var sessionStartedAt: Instant? = null

    // The device's UTC offset at that same instant, captured once alongside sessionStartedAt rather
    // than re-read from ZoneId.systemDefault() at submission time — a device timezone change mid-session
    // must not shift the streak/daily-goal study date the server derives from this value.
    private var sessionStartUtcOffsetMinutes: Int = 0

    // Test-only seam mirroring RatedStudySessionViewModel.now — production leaves this as
    // Instant::now and never overrides it.
    internal var now: () -> Instant = Instant::now

    // Guards terminate() against firing twice — a rapid double advance/exit-confirm, or a stray
    // voice-state re-collection after natural end has already fired, must not send a second
    // navigation event.
    private var terminated = false

    // A Fast card's answer being shown is the Studied criterion, tracked here
    // rather than in core:domain — Fast has no state-machine record the way Rated does, just this
    // set. A LinkedHashSet keeps first-seen order for the sealed cardResults and makes re-recording a
    // revisited card (skip-previous) a no-op, satisfying idempotency for free.
    private val seenCardIds = linkedSetOf<String>()

    // Session-start-only signal: one packed progress
    // document read per Subcategory in the route's scope, merged into cardId -> CardProgressEntry.
    // Fast has no mastery concept — only the new-entry half (a cardId absent here) matters, and even
    // that is in-session-only, feeding the new-card scoring bonus, never written to
    // FlashcardResult or any persisted document. A failed read (offline, permissions,
    // ...) leaves this empty rather than blocking the session; every card is then simply not-new for
    // scoring purposes, an acceptable price for a session that still runs. Exposed internally only
    // for test assertions — nothing in the UI reads it.
    internal var priorProgressByCardId: Map<String, CardProgressEntry> = emptyMap()
        private set

    // The XP configuration as of this session's start, fetched alongside
    // sessionStartData and never re-read — ADR-0047's snapshot rule. Defaults to XpConfig()'s own
    // defaults for the brief window before loadFlashcards' fetch resolves; abandoning before then
    // seals a placeholderResult scored against that same default, same as an empty cardResults list.
    private var sessionXpConfig: XpConfig = XpConfig()

    init {
        loadFlashcards()
        observeVoiceState()
    }

    // Card selection happens on the Preview Study Session screen (ADR-0004); the session only
    // resolves the routed cardIds to full Flashcards, preserving the routed order.
    private fun loadFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val sessionStartData = getSessionStartData(route.subcategoryIds)
            val flashcards = sessionStartData.flashcardsResult.getOrElse { _ ->
                _state.update { state -> state.copy(isLoading = false, error = R.string.study_session_load_error_message) }
                return@launch
            }
            // A failed Subcategory progress read and a never-studied one are already folded into
            // "no entries" by GetSessionStartDataUseCase — never fatal, never surfaced, exactly the
            // graceful degradation this design calls for.
            priorProgressByCardId = sessionStartData.priorProgressByCardId
            sessionXpConfig = sessionStartData.xpConfig

            val cardsById = flashcards.associateBy { it.id }
            val sessionCards = route.cardIds.mapNotNull(cardsById::get)
            _state.update {
                it.copy(
                    isLoading = false,
                    flashcards = sessionCards,
                )
            }
            // Read-aloud off is a manual tap-to-reveal/tap-to-advance session and never starts text-to-speech.
            if (route.readAloudEnabled) ensureVoiceGatewayStarted()
            // The clock starts here, once a card is actually on screen — never at route entry, so
            // a session whose card load fails never banks time.
            if (sessionCards.isNotEmpty()) startStudyClock()
        }
    }

    private fun startStudyClock() {
        val instant = now()
        sessionStartedAt = instant
        sessionStartUtcOffsetMinutes = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / SECONDS_PER_MINUTE
        clock = startClock(clock, instant)
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceGateway.state.collect { voice ->
                if (voice.error != null) {
                    voiceStarted = false
                    _state.update { it.copy(isVoiceActive = false, isVoicePlaying = false, isReadAloudMode = false) }
                    _messages.tryEmit(FastStudySessionMessage.VoicePlaybackUnavailable)
                    return@collect
                }
                // The answer phase for the current index is Fast's Studied criterion under
                // read-aloud — recorded before the natural-end check below,
                // which relies on the last card already being marked Seen.
                if (voice.isActive && voice.phase == VoicePhase.Answer) {
                    markSeen(voice.currentIndex)
                }
                val readAloudNaturalEnd = isReadAloudNaturalEnd(voice)
                _state.update {
                    it.copy(
                        isVoiceActive = voice.isActive,
                        isVoicePlaying = voice.isPlaying,
                        speechRate = voice.speechRate,
                        currentCardIndex = if (voice.isActive) voice.currentIndex else it.currentCardIndex,
                        isAnswerRevealed = if (voice.isActive) voice.phase == VoicePhase.Answer else it.isAnswerRevealed,
                    )
                }
                if (voice.isActive && voice.currentIndex != lastObservedCardIndex) {
                    lastObservedCardIndex = voice.currentIndex
                    advanceAfterExtendedContextJob?.cancel()
                    pausedDueToExtendedContext = false
                    startRewindThresholdTimer()
                } else if (!voice.isActive) {
                    voiceStarted = false
                    lastObservedCardIndex = -1
                    advanceAfterExtendedContextJob?.cancel()
                    pausedDueToExtendedContext = false
                    rewindJob?.cancel()
                    isPastRewindThreshold = false
                }
                if (voice.isInBetweenPause && voice.isPlaying && isExtendedContextDialogOpen && !pausedDueToExtendedContext) {
                    pausedDueToExtendedContext = true
                    viewModelScope.launch { voiceGateway.togglePlayPause() }
                }
                // The final card's answer is read in full before this fires — never at the moment
                // it merely started ("do not confuse answer shown with session
                // over").
                if (readAloudNaturalEnd) terminate(abandoned = false)
            }
        }
    }

    /**
     * The engine settling back on [VoicePhase.Question], not playing, at the last card is unique to
     * the TTS engine's own natural-end branch — a user-initiated pause never resets phase back to
     * Question this way, and requiring the last card to already be in [seenCardIds] rules out the
     * otherwise-identical "never started playing" resting state.
     */
    private fun isReadAloudNaturalEnd(voice: VoicePlaybackState): Boolean =
        voice.isActive &&
            !voice.isPlaying &&
            voice.phase == VoicePhase.Question &&
            voice.totalCards > 0 &&
            voice.currentIndex == voice.totalCards - 1 &&
            seenCardIds.contains(_state.value.flashcards.getOrNull(voice.currentIndex)?.id)

    /** Idempotent per card — a [linkedSetOf] no-ops a revisit via skip-previous. */
    private fun markSeen(cardIndex: Int) {
        _state.value.flashcards.getOrNull(cardIndex)?.let { seenCardIds.add(it.id) }
    }

    fun onShowAnswer() {
        if (_state.value.isVoiceActive) {
            voiceGateway.showAnswer()
        } else {
            markSeen(_state.value.currentCardIndex)
            _state.update { it.copy(isAnswerRevealed = true) }
        }
    }

    /**
     * The Fast sheet's manual advance affordance — the only way to move on without Read-aloud. Only
     * reachable once the current card's answer is revealed (the sheet shows "Show answer" until
     * then), so the last card is always fully Studied before this can end the session.
     */
    fun onNextCard() {
        val currentState = _state.value
        if (currentState.currentCardIndex >= currentState.flashcards.lastIndex) {
            terminate(abandoned = false)
        } else {
            _state.update {
                it.copy(
                    currentCardIndex = it.currentCardIndex + 1,
                    isAnswerRevealed = false,
                )
            }
        }
    }

    private fun ensureVoiceGatewayStarted() {
        if (voiceStarted) return
        with(_state.value) {
            if (flashcards.isEmpty()) return
            voiceStarted = true
            voiceGateway.start(
                cards = flashcards,
                startIndex = currentCardIndex,
                subcategoryName = sessionTitle,
            )
        }
        voiceGateway.setSpeechRate(sessionVoiceSettings.speechRate)
        voiceGateway.setVoice(sessionVoiceSettings.voiceId)
    }

    fun onVoicePlayPause() {
        if (pausedDueToExtendedContext) {
            advanceAfterExtendedContextJob?.cancel()
            pausedDueToExtendedContext = false
            viewModelScope.launch {
                voiceGateway.rewindToNext()
                voiceGateway.togglePlayPause()
            }
        } else {
            voiceGateway.togglePlayPause()
        }
    }

    fun onVoiceNext() {
        advanceAfterExtendedContextJob?.cancel()
        pausedDueToExtendedContext = false
        voiceGateway.rewindToNext()
    }

    fun onVoicePrevious() {
        advanceAfterExtendedContextJob?.cancel()
        pausedDueToExtendedContext = false
        if (isPastRewindThreshold || voiceGateway.state.value.currentIndex == 0) {
            voiceGateway.restartCurrentCard()
            startRewindThresholdTimer()
        } else {
            voiceGateway.rewindToPrevious()
        }
    }

    fun onVoiceSpeedChange(rate: Float) {
        voiceGateway.setSpeechRate(rate)
    }

    private fun onExtendedContextDialogOpen(dialog: CurrentCardExtendedContext) {
        _state.update { it.copy(activeDialog = dialog) }
        val voiceState = voiceGateway.state.value
        if (voiceState.isInBetweenPause && voiceState.isPlaying) {
            pausedDueToExtendedContext = true
            viewModelScope.launch { voiceGateway.togglePlayPause() }
        }
    }

    private fun onExtendedContextDialogDismissed() {
        if (pausedDueToExtendedContext) {
            advanceAfterExtendedContextJob = viewModelScope.launch {
                delay(EXTENDED_CONTEXT_ADVANCE_DELAY_MS.milliseconds)
                pausedDueToExtendedContext = false
                voiceGateway.rewindToNext()
                voiceGateway.togglePlayPause()
            }
        }
    }

    private fun startRewindThresholdTimer() {
        rewindJob?.cancel()
        isPastRewindThreshold = false
        rewindJob = viewModelScope.launch {
            delay(rewindThresholdMs.milliseconds)
            isPastRewindThreshold = true
        }
    }

    private fun onVoiceSettingsOpen() {
        if (_state.value.isVoicePlaying) {
            pausedForVoiceSettings = true
            voiceGateway.togglePlayPause()
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
        if (_state.value.isVoiceActive) {
            voiceGateway.setSpeechRate(settings.speechRate)
            voiceGateway.setVoice(settings.voiceId)
        }
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
            voiceGateway.togglePlayPause()
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
                terminate(abandoned = true)
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
        if (_state.value.isVoicePlaying) voiceGateway.togglePlayPause()
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

    /**
     * Both terminal paths — the deck exhausted and a confirmed "Exit session?" — run this,
     * [abandoned] the only thing differing, calling the same shared
     * `core:domain` [sealSessionResult] Rated uses rather than duplicating its clock-stamping.
     * Seals cardResults from [seenCardIds], stamps the duration off [clock], and emits the one-time
     * navigation event (ADR-0019) exactly once — [terminated] guards a stray second call. Confirming
     * "Exit session?" before any card has loaded (the X button is reachable during `isLoading`/error
     * too) seals empty cardResults with zero duration rather than crashing, mirroring
     * `RatedStudySessionViewModel.terminate` — there is nothing to have studied yet.
     */
    private fun terminate(abandoned: Boolean) {
        if (terminated) return
        terminated = true
        val at = now()
        val placeholderResult = SessionResult.Fast(
            id = sessionId,
            startedAt = sessionStartedAt ?: at,
            durationSeconds = 0, // overwritten by sealSessionResult below
            abandoned = abandoned,
            categoryId = route.categoryId,
            categoryName = route.categoryName,
            subcategoryIds = route.subcategoryIds,
            subcategoryNames = route.subcategoryNames,
            cardResults = sealFastCardResults(),
            // studyDate/dailyGoalMinutes are never read: toSummaryRoute() (below) doesn't carry
            // either — the Summary ViewModel computes real values when it reconstructs its own
            // SessionResult from the route. studyDateUtcOffsetMinutes is different: it's the real
            // value captured at session start, and toSummaryRoute() does carry it through.
            studyDate = "",
            studyDateUtcOffsetMinutes = sessionStartUtcOffsetMinutes,
            dailyGoalMinutes = 0,
            xpConfig = sessionXpConfig,
        )
        val result = sealSessionResult(result = placeholderResult, clock = clock, at = at)
        viewModelScope.launch {
            eventChannel.send(FastStudySessionDestination.Summary(result.toSummaryRoute()))
        }
    }

    /**
     * One [FlashcardResult.Fast] per [seenCardIds], in first-seen order — Fast's definition of
     * Studied. Every entry is [FlashcardStudyProgressState.Seen] — Fast has no
     * Attempts or `wasPreviouslyMastered` field to carry at all.
     */
    private fun sealFastCardResults(): List<FlashcardResult.Fast> {
        val cardsById = _state.value.flashcards.associateBy(Flashcard::id)
        return seenCardIds.mapNotNull { cardId ->
            cardsById[cardId]?.let { card ->
                FlashcardResult.Fast(
                    cardId = card.id,
                    subcategoryId = card.subcategoryId,
                    state = FlashcardStudyProgressState.Seen,
                )
            }
        }
    }

    public override fun onCleared() {
        voiceGateway.stop()
    }

    private companion object {
        const val EXTENDED_CONTEXT_ADVANCE_DELAY_MS = 500L
        const val SECONDS_PER_MINUTE = 60
    }
}
