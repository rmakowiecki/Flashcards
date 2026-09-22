package com.rossomak.flashcards.feature.study.rated

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.SessionClock
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.UserPreference.VoiceAnswerConsent as VoiceAnswerConsentPreference
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.rate
import com.rossomak.flashcards.core.domain.model.requeueAfterSilence
import com.rossomak.flashcards.core.domain.model.sealRatedCardResults
import com.rossomak.flashcards.core.domain.model.sealSessionResult
import com.rossomak.flashcards.core.domain.model.startClock
import com.rossomak.flashcards.core.domain.model.toFlashcardAttemptRating
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.toVoiceSettings
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.VoiceAnswerConsent
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.toSummaryRoute
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import com.rossomak.flashcards.feature.study.voice.VoiceGateway
import com.rossomak.flashcards.feature.study.voice.VoicePhase
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs a Rated Study Session end to end — reveal, the Failed/Partial/Correct row, and the
 * in-session voice-answering toggle with its consent and microphone flow
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)).
 * Knows nothing about Read-aloud or auto-start playback — those are Fast concepts.
 *
 * The user-preferences use cases live here and only here: their sole current purpose is the
 * voice-answering consent flag (ADR-0025), and Fast has no path to it.
 *
 * The rating callback drives a [RatedSessionState]: a Correct rating finishes a card as Mastered,
 * Failed/Partial re-insert it further down the queue (or finish it, per
 * [RatedStudySessionRoute.partialRatingCardRequeueingEnabled] and the Attempts limit), and the
 * session's terminal navigation event fires once the queue empties. A voice grade drives the exact same [onAttemptRating] path as a manual tap; a
 * silence timeout instead consumes no Attempt, and three in a row pause the session rather than
 * finishing it.
 */
@HiltViewModel
class RatedStudySessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSessionStartData: GetSessionStartDataUseCase,
    private val submitCurationReport: SubmitCurationReportUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
    private val voiceGateway: VoiceGateway,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<RatedStudySessionRoute>()

    // Distinct from the screen's own per-card title (chrome.studySessionCardTitle):
    // this is the fixed name the voice gateway's notification shows for the whole session.
    private val sessionTitle: String = route.sessionTitle

    private val _state = MutableStateFlow(
        RatedStudySessionScreenState(
            categoryName = route.categoryName,
            subcategoryNameById = route.subcategoryIds.zip(route.subcategoryNames).toMap(),
            attemptsLimit = route.ratedAttempts,
        ),
    )
    val state: StateFlow<RatedStudySessionScreenState> = _state.asStateFlow()

    // Tracks eagerly so rapid toggles don't race against isVoiceActive propagation.
    private var voiceStarted = false

    internal var rewindThresholdMs: Long = VoicePlaybackState.REWIND_THRESHOLD_MS

    // Test-only seam for asserting a deterministic queue sequence (ADR-0046) — production leaves
    // this as Random.Default and never seeds it.
    internal var random: Random = Random.Default

    // Test-only seam mirroring random above — production leaves this as Instant::now and never
    // overrides it.
    internal var now: () -> Instant = Instant::now

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

    // Guards terminate() against firing twice — natural end (onAttemptRating) and a confirmed "Exit
    // session?" can otherwise both fire if the dialog is already open the instant the last card
    // resolves, sending a second Summary navigation event. Mirrors FastStudySessionViewModel's
    // identical guard.
    private var terminated = false

    private var rewindJob: Job? = null
    private var isPastRewindThreshold = false
    private val eventChannel = Channel<RatedStudySessionDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var lastObservedCardIndex = -1

    // Seeded once the routed cards resolve (loadFlashcards); null only during that initial load.
    private var ratedSessionState: RatedSessionState? = null

    // Session-start-only signal: one packed progress
    // document read per Subcategory in the route's scope, merged into cardId -> CardProgressEntry.
    // Its scope is the session's scope, decided before anything is studied — it can end up strictly
    // larger than what SubmitStudySessionUseCase's own prior-state read later touches (an abandoned
    // session, or a drawn Subcategory never reached), and that is not a bug to reconcile, just waste.
    // A failed read (offline, permissions, ...) leaves this empty rather than blocking the session;
    // every card is then simply not-previously-mastered / new — the server-authoritative
    // `submitStudySession` Cloud Function never trusts this signal either, re-reading prior
    // progress itself before deciding what actually gets written.
    // Exposed internally only for test assertions — nothing in the UI reads it.
    internal var priorProgressByCardId: Map<String, CardProgressEntry> = emptyMap()
        private set

    // The XP configuration as of this session's start, fetched alongside
    // sessionStartData and never re-read — ADR-0047's snapshot rule. Defaults to XpConfig()'s own
    // defaults for the brief window before loadFlashcards' fetch resolves; abandoning before then
    // seals a placeholderResult scored against that same default, same as an empty cardResults list.
    private var sessionXpConfig: XpConfig = XpConfig()

    private val isExtendedContextDialogOpen: Boolean
        get() = _state.value.activeDialog is CurrentCardExtendedContext

    // True only when the pause was caused by the dialog intercepting a natural between-card advance.
    // Gates auto-advance on dialog dismiss and changes play-button behavior.
    private var pausedDueToExtendedContext = false
    private var advanceAfterExtendedContextJob: Job? = null

    // True only when opening voice settings paused an in-progress playback; gates resume on close.
    private var pausedForVoiceSettings = false

    private var hasVoiceAnswerConsent = false

    // Edge-detects a fresh arrival at SpeakingNotice in observeVoiceAnswerState — the collector
    // sees every VoiceAnswerState the gateway emits, but a grade/silence-timeout must apply exactly
    // once per round, not once per equal-value re-collection.
    private var previousVoiceAnswerPhase = VoiceAnswerPhase.Idle

    // Holds the screen-visible half of a voice-graded rating or silence-timeout (queue reseed,
    // currentCard/currentCardRatings, answer-reveal reset, terminal navigation) while the grade or
    // skip notice is still being spoken. The queue reducer itself (ratedSessionState) still updates
    // immediately — only what the user sees is held back — so the top of the screen keeps showing
    // the card the feedback is actually about instead of jumping to the next question mid-notice.
    // Runs the moment the phase leaves SpeakingNotice (see observeVoiceAnswerState), whatever the
    // reason (notice finished naturally, or voice answering was torn down mid-notice).
    private var pendingSessionSync: (() -> Unit)? = null

    // Session-scoped, not per-card: counts consecutive silence timeouts, reset by any
    // graded answer, and pauses the session on reaching CONSECUTIVE_SILENCE_PAUSE_THRESHOLD.
    private var consecutiveSilenceCount = 0

    // Session-scoped like the rest of the routed config: a mid-session change updates only this
    // running session unless the user checks "keep as my default" (ADR-0030), so it lives in a
    // plain var rather than being re-read from the controller on every playback start.
    private var sessionVoiceSettings: SavedVoiceSettings = route.voiceSettings

    init {
        loadFlashcards()
        observeVoiceState()
        observeVoiceAnswerState()
        observeVoiceAnswerConsentState()
    }

    // Card selection happens on the Preview Study Session screen (ADR-0004); the session only
    // resolves the routed cardIds to full Flashcards, preserving the routed order.
    private fun loadFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val sessionStartData = getSessionStartData(route.subcategoryIds)
            val flashcards = sessionStartData.flashcardsResult.getOrElse { _ ->
                _state.update { state -> state.copy(isLoading = false, error = "Could not load flashcards") }
                return@launch
            }
            // A failed Subcategory progress read and a never-studied one are already folded into
            // "no entries" by GetSessionStartDataUseCase — never fatal, never surfaced, exactly the
            // graceful degradation this design calls for.
            priorProgressByCardId = sessionStartData.priorProgressByCardId
            sessionXpConfig = sessionStartData.xpConfig

            val cardsById = flashcards.associateBy { it.id }
            val sessionCards = route.cardIds.mapNotNull(cardsById::get)
            val previouslyMasteredCardIds = priorProgressByCardId
                .filterValues { it.state == FlashcardStudyProgressState.Mastered }
                .keys
            ratedSessionState = RatedSessionState.seed(
                cards = sessionCards,
                attemptsLimit = route.ratedAttempts,
                partialRatingCardRequeueingEnabled = route.partialRatingCardRequeueingEnabled,
                random = random,
                previouslyMasteredCardIds = previouslyMasteredCardIds,
            )
            _state.update { it.copy(isLoading = false) }
            syncStateFromRatedSession()
            // The clock starts here, once a card is actually on screen — never at route entry, so
            // a session whose card load fails never banks time.
            if (sessionCards.isNotEmpty()) startStudyClock()
            honourRoutedVoiceAnswering(hasCards = sessionCards.isNotEmpty())
        }
    }

    private fun startStudyClock() {
        val instant = now()
        sessionStartedAt = instant
        sessionStartUtcOffsetMinutes = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / SECONDS_PER_MINUTE
        clock = startClock(clock, instant)
    }

    /**
     * Mirrors the machine's queue into screen state. [RatedStudySessionScreenState.currentCardIndex]
     * always lands on 0 in this path — the current card is always the queue's head.
     *
     * Also re-seeds the voice engine's queue whenever voice is active: [VoiceGateway.updateQueue]
     * swaps in [RatedSessionState.remainingCards] without touching the in-flight utterance, keeping
     * the spoken card, displayed card, and reducer head from diverging once a rating or silence
     * timeout reorders the queue (ADR-0046).
     */
    private fun syncStateFromRatedSession() {
        val machine = ratedSessionState ?: return
        _state.update {
            it.copy(
                flashcards = machine.remainingCards,
                currentCardIndex = 0,
                masteredCount = machine.masteredCount,
                completedCount = machine.completedCount,
                distinctCardCount = machine.distinctCardCount,
                currentCardRatings = machine.currentCardRatings,
            )
        }
        // voiceStarted, not just isVoiceActive: a rating can land after voiceGateway.start() was
        // called but before the async bind actually completes (isVoiceActive still false at that
        // point) — StudySessionVoiceGateway.updateQueue() unconditionally updates its pendingCards
        // regardless of bind state, so this still reaches the gateway before onServiceConnected()
        // loads it, rather than leaving it to load the stale pre-rating order.
        if (_state.value.isVoiceActive || voiceStarted) {
            voiceGateway.updateQueue(machine.remainingCards)
        }
    }

    /**
     * The Preview screen's voice-answering choice (ADR-0030) takes effect on entry, running the
     * same consent-then-microphone path the in-session toggle uses.
     *
     * Consent is read as a one-shot rather than from [hasVoiceAnswerConsent], whose collector may
     * not have emitted yet by the time the cards land.
     */
    private suspend fun honourRoutedVoiceAnswering(hasCards: Boolean) {
        if (!route.voiceAnsweringEnabled || !hasCards) return
        requestVoiceAnswering(observeUserPreferences().first().voiceAnswerConsentGranted)
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceGateway.state.collect { voice ->
                if (voice.error != null) {
                    voiceStarted = false
                    _state.update { it.copy(isVoiceActive = false, isVoicePlaying = false, voiceError = voice.error) }
                    return@collect
                }
                _state.update {
                    it.copy(
                        isVoiceActive = voice.isActive,
                        isVoicePlaying = voice.isPlaying,
                        speechRate = voice.speechRate,
                        currentCardIndex = if (voice.isActive) voice.currentIndex else it.currentCardIndex,
                        // Grading/feedback also reveals the card (see observeVoiceAnswerState) —
                        // don't let this collector's phase check stomp that back to false while
                        // the TTS engine itself is still sitting on QUESTION.
                        isAnswerRevealed = if (voice.isActive) {
                            voice.phase == VoicePhase.Answer ||
                                it.voiceAnswerPhase == VoiceAnswerPhase.Grading ||
                                it.voiceAnswerPhase == VoiceAnswerPhase.SpeakingNotice
                        } else {
                            it.isAnswerRevealed
                        },
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
            }
        }
    }

    private fun observeVoiceAnswerState() {
        viewModelScope.launch {
            voiceGateway.voiceAnswerState.collect { voiceAnswer ->
                // Edge-detected before the state update below, off the collector's own running
                // previousVoiceAnswerPhase — SpeakingNotice is entered exactly once per graded or
                // silence-timed-out round, never re-triggered by an equal-value re-collection.
                val justEnteredSpeakingNotice = voiceAnswer.phase == VoiceAnswerPhase.SpeakingNotice &&
                    previousVoiceAnswerPhase != VoiceAnswerPhase.SpeakingNotice
                // Mirrors justEnteredSpeakingNotice the other way: fires exactly once, the instant
                // the grade/skip notice stops being the active phase — whether that's the natural
                // WaitingForQuestion it flips to once the notice finishes speaking, or voice
                // answering getting torn down mid-notice. Either way the deferred sync below is safe
                // to run: it's idempotent and there is nothing left mid-notice to interrupt.
                val justLeftSpeakingNotice = previousVoiceAnswerPhase == VoiceAnswerPhase.SpeakingNotice &&
                    voiceAnswer.phase != VoiceAnswerPhase.SpeakingNotice
                previousVoiceAnswerPhase = voiceAnswer.phase
                _state.update {
                    it.copy(
                        isVoiceAnswerEnabled = voiceAnswer.isEnabled,
                        voiceAnswerPhase = voiceAnswer.phase,
                        voiceAnswerSanitizedTranscript = voiceAnswer.sanitizedTranscript,
                        lastVoiceAnswerGrade = voiceAnswer.lastGrade,
                        voiceAnswerError = voiceAnswer.error,
                        // Grading starts as soon as the utterance is captured, before the TTS
                        // engine's own phase would flip to ANSWER — reveal the card now so the
                        // user can check what they missed while grading/feedback plays out.
                        isAnswerRevealed = it.isAnswerRevealed ||
                            voiceAnswer.phase == VoiceAnswerPhase.Grading,
                    )
                }
                if (justLeftSpeakingNotice) {
                    pendingSessionSync?.invoke()
                    pendingSessionSync = null
                }
                if (!justEnteredSpeakingNotice) return@collect
                // ADR-0026: lastGrade == null distinguishes a silence-timeout skip from a real
                // graded result — both share SpeakingNotice, never a dedicated phase value. But a
                // grading/transcription failure also lands in SpeakingNotice with lastGrade == null
                // (VoiceAnswerController's catch block never sets a grade), so error must be ruled
                // out first or a backend failure gets silently counted as silence.
                val grade = voiceAnswer.lastGrade
                when {
                    grade != null -> onVoiceGraded(grade, voiceAnswer.lastGradedCardId)
                    // A grading/transcription failure is not counted as a silence timeout — it
                    // is simply ignored, leaving the queue and consecutiveSilenceCount untouched.
                    voiceAnswer.error != null -> Unit
                    else -> onVoiceSilenceTimeout()
                }
            }
        }
    }

    /**
     * The one path a voice grade applies a Rating through — [onAttemptRating] itself, exactly like a
     * manual tap, using the fixed grade-band mapping. An actual graded utterance is the only proof someone is there, so this is also the
     * one place [consecutiveSilenceCount] resets.
     *
     * [gradedCardId] guards against grading a card the reducer head has already moved past — the
     * Rated voice transport still allows Next while a question is being read (before listening
     * opens), so a grade can in principle land for a card that isn't the current head any more. A
     * mismatch means this grade is stale; drop it rather than rating whatever the head currently is.
     */
    private fun onVoiceGraded(grade: VoiceAnswerGrade, gradedCardId: String?) {
        val headCardId = ratedSessionState?.currentCard?.id
        if (gradedCardId != null && gradedCardId != headCardId) return
        consecutiveSilenceCount = 0
        applyAttemptRating(grade.toFlashcardAttemptRating(), deferSync = true)
    }

    /**
     * A silence timeout: no Attempt, no Rating — the card is put back unchanged, using the Failed
     * gap range. Three in a row pauses the session rather than letting an unattended phone cycle
     * the deck indefinitely.
     *
     * The reducer updates right away, but what the screen shows waits like [applyAttemptRating]'s deferred
     * path does — the "didn't hear you" notice is about the still-displayed card, so the queue's
     * next head must not appear until that notice finishes.
     */
    private fun onVoiceSilenceTimeout() {
        ratedSessionState = ratedSessionState?.let(::requeueAfterSilence)
        consecutiveSilenceCount++
        pendingSessionSync = { syncStateFromRatedSession() }
        if (consecutiveSilenceCount >= CONSECUTIVE_SILENCE_PAUSE_THRESHOLD) {
            pauseForRepeatedSilence()
        }
    }

    /**
     * Pausing is not ending: no Terminal State, no navigation event, the queue untouched. Playback
     * and the microphone stop; only the resume affordance stays live.
     */
    private fun pauseForRepeatedSilence() {
        if (_state.value.isVoicePlaying) voiceGateway.togglePlayPause()
        voiceGateway.setVoiceAnswering(false)
        _state.update { it.copy(isVoiceAnswerPaused = true) }
    }

    /** Re-arms voice answering on the same card, counter back at zero. */
    fun onResumeSession() {
        consecutiveSilenceCount = 0
        _state.update { it.copy(isVoiceAnswerPaused = false) }
        voiceGateway.setVoiceAnswering(true)
        if (!_state.value.isVoicePlaying) voiceGateway.togglePlayPause()
    }

    private fun observeVoiceAnswerConsentState() {
        viewModelScope.launch {
            observeUserPreferences().map { it.voiceAnswerConsentGranted }.collect { hasConsent ->
                hasVoiceAnswerConsent = hasConsent
            }
        }
    }

    fun onVoiceAnswerToggle() {
        if (_state.value.isVoiceAnswerEnabled) {
            // Voice-answering-on drives the shared TTS engine in a stop-after-question shape;
            // there is no meaningful "keep reading, just stop grading" middle state (ADR-0025),
            // so disabling it tears down the whole engine back to manual Show Answer/Next.
            voiceGateway.stop()
            return
        }
        requestVoiceAnswering(hasVoiceAnswerConsent)
    }

    /** Consent first, then the microphone. Both gates are one-time; neither is skippable. */
    private fun requestVoiceAnswering(hasConsent: Boolean) {
        if (hasConsent) {
            _state.update { it.copy(isMicPermissionRequestPending = true) }
        } else {
            _state.update { it.copy(activeDialog = VoiceAnswerConsent) }
        }
    }

    private fun onVoiceAnswerConsentAccept() {
        viewModelScope.launch {
            saveUserPreference(VoiceAnswerConsentPreference(true))
                .onSuccess {
                    _state.update {
                        it.copy(
                            activeDialog = null,
                            isMicPermissionRequestPending = true,
                        )
                    }
                }
                .onFailure {
                    // Consent wasn't actually recorded — leave the dialog up rather than starting
                    // the mic as if it had been, so a retry is a single tap on the same dialog.
                    _state.update { it.copy(voiceError = "Failed to save voice answering consent") }
                }
        }
    }

    fun onMicPermissionResult(isGranted: Boolean) {
        _state.update { it.copy(isMicPermissionRequestPending = false) }
        if (!isGranted) return
        // Rated sessions never auto-start the gateway; enabling voice answering is what
        // bootstraps it here (ADR-0025).
        ensureVoiceGatewayStarted()
        voiceGateway.setVoiceAnswering(true)
    }

    fun onVoiceAnswerGradeDismissed() {
        _state.update { it.copy(lastVoiceAnswerGrade = null) }
    }

    fun onShowAnswer() {
        if (_state.value.isVoiceActive) {
            voiceGateway.showAnswer()
        } else {
            _state.update { it.copy(isAnswerRevealed = true) }
        }
    }

    /**
     * Applies [rating] to the machine's current (head) card: Correct finishes it Mastered
     * immediately, Failed/Partial either re-insert it further down the queue or finish it, per the
     * Attempts limit and [RatedStudySessionRoute.partialRatingCardRequeueingEnabled]. The session
     * completes — and the terminal navigation event fires — exactly when the queue empties.
     */
    fun onAttemptRating(rating: FlashcardAttemptRating) = applyAttemptRating(rating, deferSync = false)

    /**
     * [deferSync] is what separates a manual tap from a voice grade: a tap has no feedback playing
     * over it, so the queue advance is immediate exactly like before. A voice grade instead lands
     * mid-[VoiceAnswerPhase.SpeakingNotice] — the feedback about to be read is about the card still
     * on screen, so the queue reducer updates now (the [VoiceGateway] still needs the reordered
     * queue reseeded to know what's next once the notice ends) but everything the user actually
     * sees — [RatedStudySessionScreenState.currentCard]/`currentCardRatings`, the answer-reveal
     * reset, and the terminal navigation event — is captured into [pendingSessionSync] and only
     * runs once that notice actually finishes (observeVoiceAnswerState's SpeakingNotice-exit edge).
     */
    private fun applyAttemptRating(rating: FlashcardAttemptRating, deferSync: Boolean) {
        val machine = ratedSessionState ?: return
        // A rapid second tap, or a late voice grade/silence timeout racing the terminal navigation
        // event, can still reach here after the queue has emptied — rate() assumes a head to rate.
        if (machine.isComplete) return
        val outcome = rate(machine, rating)
        ratedSessionState = outcome.state
        val applyEffects = {
            _state.update { it.copy(isAnswerRevealed = false) }
            syncStateFromRatedSession()
            if (outcome.state.isComplete) terminate(abandoned = false)
        }
        if (deferSync) pendingSessionSync = applyEffects else applyEffects()
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
                delay(EXTENDED_CONTEXT_ADVANCE_DELAY_MS)
                pausedDueToExtendedContext = false
                voiceGateway.rewindToNext()
                voiceGateway.togglePlayPause()
            }
        }
    }

    fun onVoiceErrorDismissed() {
        _state.update { it.copy(voiceError = null) }
    }

    private fun startRewindThresholdTimer() {
        rewindJob?.cancel()
        isPastRewindThreshold = false
        rewindJob = viewModelScope.launch {
            delay(rewindThresholdMs)
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
     * This adds only what the call site could not: the playback side effects, and the voice-settings
     * draftState, which comes from the shared controller rather than screen state.
     */
    private fun onDialogOpen(dialog: StudySessionDialog) {
        when (dialog) {
            is ReportCurrentCardProblem -> onReportProblemOpen(dialog)
            is CurrentCardExtendedContext -> onExtendedContextDialogOpen(dialog)
            is SessionVoiceSettings -> onVoiceSettingsOpen()
            VoiceAnswerConsent, ExitSession ->
                _state.update { it.copy(activeDialog = dialog) }
        }
    }

    /**
     * Stores the draftState the host built, then fires any side effect the edit implies.
     *
     * The side effect comes from diffing the previous draftState against the next rather than from an
     * event that names the changed field: it keeps every dialog on the one generic
     * [StudySessionDialogEvent.DraftChange], and puts the trigger somewhere a unit test can reach
     * (ADR-0036).
     */
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
            VoiceAnswerConsent -> onVoiceAnswerConsentAccept()
            is SessionVoiceSettings -> onVoiceSettingsSave()
            ExitSession -> {
                onDialogDismiss()
                terminate(abandoned = true)
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
                _state.update { it.copy(curationError = "Failed to submit report") }
            }
        }
    }

    /**
     * Both terminal paths — the last card resolving and a confirmed "Exit session?" — run this,
     * [abandoned] the only thing differing. Seals cardResults from whatever the
     * state machine has resolved so far, stamps the duration off [clock], and emits the one-time
     * navigation event (ADR-0019) exactly once — [terminated] guards a stray second call, e.g. the
     * exit dialog being confirmed the instant after the last card's rating already completed the
     * deck and sent its own Summary event. A `null` [ratedSessionState] (abandoning before
     * flashcards ever finished loading) seals empty cardResults with zero duration rather than
     * crashing — there is nothing to have studied yet.
     */
    private fun terminate(abandoned: Boolean) {
        if (terminated) return
        terminated = true
        val at = now()
        val cardResults = ratedSessionState?.let { sealRatedCardResults(it, abandoned) } ?: emptyList()
        val placeholderResult = SessionResult.Rated(
            id = sessionId,
            startedAt = sessionStartedAt ?: at,
            durationSeconds = 0, // overwritten by sealSessionResult below
            abandoned = abandoned,
            categoryId = route.categoryId,
            categoryName = route.categoryName,
            subcategoryIds = route.subcategoryIds,
            subcategoryNames = route.subcategoryNames,
            cardResults = cardResults,
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
            eventChannel.send(RatedStudySessionDestination.Summary(result.toSummaryRoute()))
        }
    }

    fun onCurationErrorDismissed() {
        _state.update { it.copy(curationError = null) }
    }

    public override fun onCleared() {
        voiceGateway.stop()
    }

    private companion object {
        const val EXTENDED_CONTEXT_ADVANCE_DELAY_MS = 500L
        const val CONSECUTIVE_SILENCE_PAUSE_THRESHOLD = 3
        const val SECONDS_PER_MINUTE = 60
    }
}
