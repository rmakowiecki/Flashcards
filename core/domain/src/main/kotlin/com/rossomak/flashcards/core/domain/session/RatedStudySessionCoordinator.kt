package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.DomainLogger
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.isShort
import com.rossomak.flashcards.core.domain.model.sealRatedCardResults
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.VoiceAnswerGradingRepository
import com.rossomak.flashcards.core.domain.repository.VoiceCaptureGateway
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.AdvanceAfterVoiceAnswer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelGrading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Grade
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.OpenListeningWindow
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.RestartCurrentCard
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SpeakNotice
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartNoticeTail
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartVoiceAnswering
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopListening
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopVoiceAnswering
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopVoiceStack
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SyncQueue
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import java.time.Clock
import javax.inject.Inject
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Runs one Rated Study Session: loads its cards, holds the [RatedSessionReducer] state, runs every
 * effect and timer, and owns the queue, the advance, the Voice Answering round, every transport
 * command (in-app or from outside the app), the session clock and the result
 * ([ADR-0054](../../../../../../../docs/adr/0054-study-session-rules-in-domain-coordinators.md)).
 *
 * Its ViewModel calls [start] with `viewModelScope` and [stop] from `onCleared`. It never creates a
 * scope of its own. A voice-answering session keeps its delivery mode to the end: an unavailable
 * voice engine pauses it, and [resume] restarts the voice stack.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one collaborator per seam, one command per session action.
class RatedStudySessionCoordinator @Inject constructor(
    private val getSessionStartData: GetSessionStartDataUseCase,
    private val playbackGateway: StudyVoicePlaybackGateway,
    private val captureGateway: VoiceCaptureGateway,
    private val gradingRepository: VoiceAnswerGradingRepository,
    private val permissionGateway: PermissionGateway,
    private val reducer: RatedSessionReducer,
    clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks,
    private val logger: DomainLogger,
) {
    private val _sessionState = MutableStateFlow<RatedSessionStateSnapshot>(RatedSessionStateSnapshot.Loading)
    val sessionState: StateFlow<RatedSessionStateSnapshot> = _sessionState.asStateFlow()

    private val eventChannel = Channel<RatedSessionEvent>(Channel.UNLIMITED)
    val events: Flow<RatedSessionEvent> = eventChannel.receiveAsFlow()

    private val timekeeper = SessionTimekeeper(clock)

    private var scope: CoroutineScope? = null
    private lateinit var setup: RatedSessionSetup
    private lateinit var voiceSettings: VoiceSettings

    // null until the cards load; inputs arriving before then have nothing to act on.
    private var state: RatedSessionState? = null
    private var playback = VoicePlaybackState()

    private val pendingInputs = ArrayDeque<RatedSessionInput>()
    private var isDispatching = false

    private var isObservingVoiceStack = false
    private var isVoiceStackStarted = false
    private var listeningJob: Job? = null
    private var gradingJob: Job? = null
    private var noticeTailJob: Job? = null
    private var isMicPermissionRevoked = false
    private var hasEnded = false

    /**
     * The session-start progress read, merged across the routed Subcategories. A failed read leaves
     * it empty rather than blocking the session: every card then simply counts as not previously
     * mastered. The server re-reads prior progress itself and never trusts this.
     */
    internal var priorProgressByCardId: Map<String, CardProgressEntry> = emptyMap()
        private set

    // ADR-0047's snapshot rule: fetched once at load and never re-read. Defaults until then, so an
    // exit before the load finishes is scored against the defaults.
    private var xpConfig = XpConfig()

    fun start(scope: CoroutineScope, setup: RatedSessionSetup) {
        check(this.scope == null) { "A coordinator runs one session" }
        this.scope = scope
        this.setup = setup
        voiceSettings = setup.voiceSettings
        scope.launch { load() }
    }

    /** Synchronous: `viewModelScope` is already cancelled when `onCleared` runs. */
    fun stop() {
        listeningJob?.cancel()
        gradingJob?.cancel()
        noticeTailJob?.cancel()
        captureGateway.stopVoiceAnswering()
        playbackGateway.stop()
        isVoiceStackStarted = false
    }

    fun rate(rating: FlashcardAttemptRating) = dispatch(RatedSessionInput.AttemptRated(rating))

    fun revealAnswer() {
        if (playback.isActive) playbackGateway.showAnswer()
        dispatch(RatedSessionInput.AnswerRevealed)
    }

    fun play() {
        if (state?.pauseReason == SessionPauseReason.VoiceEngineUnavailable) resume() else dispatch(RatedSessionInput.PlayRequested)
    }

    fun pause() = dispatch(RatedSessionInput.PauseRequested)

    fun next() = dispatch(RatedSessionInput.CardSkipped)

    fun previous() = dispatch(RatedSessionInput.PreviousRequested)

    /**
     * Resumes a paused session, with the microphone permission checked first (ADR-0052). After an
     * engine failure the whole voice stack starts again at the presented card; after a voice-answer
     * pause, voice answering starts again on the same card.
     */
    fun resume() {
        val current = state ?: return
        requireNotNull(scope).launch {
            val isEnginePause = current.pauseReason == SessionPauseReason.VoiceEngineUnavailable
            if (!isEnginePause && current.voiceAnswerPauseReason == null) return@launch
            if (current.isVoiceAnsweringSession && !isMicrophoneGranted()) {
                onMicPermissionRevoked()
                return@launch
            }
            if (isEnginePause) {
                playbackGateway.stop()
                startVoiceStack(startVoiceAnswering = false)
                dispatch(RatedSessionInput.VoiceStackRestarted)
            } else {
                dispatch(RatedSessionInput.VoiceAnsweringResumed)
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        voiceSettings = voiceSettings.copy(speechRate = rate)
        playbackGateway.setSpeechRate(rate)
    }

    /** Applies to the rest of this session, including a voice stack restarted later. */
    fun applyVoiceSettings(settings: VoiceSettings) {
        voiceSettings = settings
        if (playback.isActive) {
            playbackGateway.setSpeechRate(settings.speechRate)
            playbackGateway.setVoice(settings.voiceId)
        }
    }

    /**
     * Seals the result exactly once and reports it as [RatedSessionEvent.SessionEnded], whether the
     * last card finished or the user left. Leaving before the cards load seals empty card results
     * with zero duration.
     */
    fun end(abandoned: Boolean) {
        if (hasEnded) return
        hasEnded = true
        val cardResults = state?.let { sealRatedCardResults(it, abandoned) } ?: emptyList()
        val result = timekeeper.seal { at ->
            SessionResult.Rated(
                id = timekeeper.sessionId,
                startedAt = timekeeper.startedAt ?: at,
                durationSeconds = 0, // overwritten by the seal
                abandoned = abandoned,
                categoryId = setup.categoryId,
                categoryName = setup.categoryName,
                subcategoryIds = setup.subcategoryIds,
                subcategoryNames = setup.subcategoryNames,
                cardResults = cardResults,
                // studyDate and dailyGoalMinutes are never read from here: the Summary screen
                // computes the real values when it rebuilds the result. The UTC offset is the real
                // value captured at session start.
                studyDate = "",
                studyDateUtcOffsetMinutes = timekeeper.utcOffsetMinutes,
                dailyGoalMinutes = 0,
                xpConfig = xpConfig,
            )
        }
        eventChannel.trySend(RatedSessionEvent.SessionEnded(result))
    }

    // Card selection happens on the Preview screen (ADR-0004); the session only resolves the routed
    // ids, in routed order.
    private suspend fun load() {
        val sessionStartData = getSessionStartData(setup.subcategoryIds)
        val flashcards = sessionStartData.flashcardsResult.getOrElse {
            _sessionState.value = RatedSessionStateSnapshot.LoadFailed
            return
        }
        priorProgressByCardId = sessionStartData.priorProgressByCardId
        xpConfig = sessionStartData.xpConfig
        val cardsById = flashcards.associateBy { it.id }
        val sessionCards = setup.cardIds.mapNotNull(cardsById::get)
        val previouslyMasteredCardIds = priorProgressByCardId
            .filterValues { it.state == FlashcardStudyProgressState.Mastered }
            .keys
        state = reducer.seed(
            cards = sessionCards,
            attemptsLimit = setup.attemptsLimit,
            partialRatingCardRequeueingEnabled = setup.partialRatingCardRequeueingEnabled,
            previouslyMasteredCardIds = previouslyMasteredCardIds,
            isVoiceAnsweringSession = setup.voiceAnsweringEnabled && sessionCards.isNotEmpty(),
        )
        publish()
        if (sessionCards.isEmpty()) return
        timekeeper.start()
        if (!setup.voiceAnsweringEnabled) return
        // Preview only starts a voice-answering session with the microphone granted; a session
        // restored after it was revoked in system Settings ends instead.
        if (isMicrophoneGranted()) startVoiceStack(startVoiceAnswering = true) else onMicPermissionRevoked()
    }

    /**
     * Question-only mode is fixed for the whole session: a play while voice answering is paused
     * reads the question and stops, and never falls back to reading answers.
     */
    private fun startVoiceStack(startVoiceAnswering: Boolean) {
        val current = state ?: return
        observeVoiceStack()
        isVoiceStackStarted = true
        playbackGateway.start(
            cards = current.remainingCards,
            startIndex = 0,
            sessionTitle = setup.sessionTitle,
            isVoiceAnsweringSession = true,
        )
        playbackGateway.setSpeechRate(voiceSettings.speechRate)
        playbackGateway.setVoice(voiceSettings.voiceId)
        playbackGateway.setQuestionOnlyMode(true)
        if (startVoiceAnswering) captureGateway.startVoiceAnswering()
    }

    private fun observeVoiceStack() {
        if (isObservingVoiceStack) return
        isObservingVoiceStack = true
        val scope = requireNotNull(scope)
        scope.launch {
            playbackGateway.state.collect { playbackState ->
                playback = playbackState
                dispatch(RatedSessionInput.PlaybackChanged(playbackState))
            }
        }
        scope.launch { playbackGateway.playbackEvents.collect(::onPlaybackEvent) }
        scope.launch { captureGateway.captureEvents.collect(::onCaptureEvent) }
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.ExternalCommand -> onExternalCommand(event.command)
            is PlaybackEvent.QuestionFinished -> dispatch(RatedSessionInput.QuestionFinished(event.cardId))
            is PlaybackEvent.NoticeFinished -> dispatch(RatedSessionInput.NoticeFinished(event.notice))
            PlaybackEvent.EngineUnavailable -> dispatch(RatedSessionInput.PlaybackEngineUnavailable)
            // Question-only reads never reach the end; the queue decides when a Rated session is over.
            PlaybackEvent.EndReached -> Unit
        }
    }

    /** Applied exactly like the matching in-app command. A controller's stop only pauses. */
    private fun onExternalCommand(command: TransportCommand) {
        when (command) {
            TransportCommand.Play -> play()
            TransportCommand.Pause, TransportCommand.Stop -> pause()
            TransportCommand.Next -> next()
            TransportCommand.Previous, TransportCommand.PreviousCard -> previous()
            is TransportCommand.JumpTo -> Unit
        }
    }

    private fun onCaptureEvent(event: CaptureEvent) {
        val input = when (event) {
            CaptureEvent.SpeechStarted -> RatedSessionInput.SpeechStarted
            CaptureEvent.SpeechEnded -> RatedSessionInput.SpeechEnded
            is CaptureEvent.UtteranceCaptured -> RatedSessionInput.UtteranceCaptured(event.obfuscatedWav)
            is CaptureEvent.CaptureFailed -> RatedSessionInput.CaptureFailed(event.reason)
        }
        dispatch(input)
    }

    /** Runs one input at a time, in arrival order, even when an effect feeds another input back. */
    private fun dispatch(input: RatedSessionInput) {
        pendingInputs.addLast(input)
        if (isDispatching) return
        isDispatching = true
        try {
            while (pendingInputs.isNotEmpty()) {
                val next = pendingInputs.removeFirst()
                val current = state ?: continue
                val transition = reducer.reduce(current, next)
                state = transition.state
                transition.effects.forEach(::run)
            }
        } finally {
            isDispatching = false
        }
        publish()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per effect, exhaustive over the sealed type.
    private fun run(effect: RatedSessionEffect) {
        val scope = requireNotNull(scope)
        when (effect) {
            is SyncQueue -> if (isVoiceStackStarted) playbackGateway.updateQueue(effect.cards)
            AdvanceAfterVoiceAnswer -> playbackGateway.advanceAfterVoiceAnswer()
            // The silence timer only starts once the route is ready and the microphone is open.
            is OpenListeningWindow -> {
                listeningJob?.cancel()
                listeningJob = scope.launch {
                    captureGateway.awaitRouteReady()
                    captureGateway.startListening()
                    delay(SILENCE_TIMEOUT)
                    dispatch(RatedSessionInput.SilenceTimedOut)
                }
            }
            StopListening -> {
                listeningJob?.cancel()
                captureGateway.stopListening()
            }
            CancelSilenceTimer -> listeningJob?.cancel()
            is Grade -> grade(effect)
            CancelGrading -> gradingJob?.cancel()
            is SpeakNotice -> playbackGateway.speakNotice(effect.notice)
            StartNoticeTail -> {
                noticeTailJob?.cancel()
                noticeTailJob = scope.launch {
                    delay(NOTICE_TAIL)
                    dispatch(RatedSessionInput.NoticeTailElapsed)
                }
            }
            PausePlayback -> playbackGateway.pause()
            Play -> playbackGateway.play()
            RestartCurrentCard -> playbackGateway.restartCurrentCard()
            StopVoiceAnswering -> {
                listeningJob?.cancel()
                captureGateway.stopVoiceAnswering()
            }
            StartVoiceAnswering -> captureGateway.startVoiceAnswering()
            StopVoiceStack -> {
                logger.warn { "Voice engine unavailable, Rated session paused" }
                stopVoiceStack()
            }
            is Emit -> eventChannel.trySend(effect.event)
            SessionComplete -> end(abandoned = false)
        }
    }

    /**
     * The grade and a failure are both held until the transcript has been on screen for its minimum
     * time. The recorded answer is only ever handed to this call.
     */
    private fun grade(effect: Grade) {
        gradingJob?.cancel()
        gradingJob = requireNotNull(scope).launch {
            gradingRepository
                .transcribeAndGradeSpokenAnswer(
                    cardId = effect.card.id,
                    question = effect.card.question,
                    expectedAnswer = effect.card.answer,
                    obfuscatedAnswerWav = effect.obfuscatedWav,
                )
                .holdGradedUntilTranscriptShown(timeSource = timeSource)
                .catch { exception ->
                    logger.error(exception) { "Voice answer grading threw instead of failing" }
                    emit(VoiceAnswerGradingEvent.Failed(GradingFailureReason.ServiceError))
                }
                .collect { event ->
                    when (event) {
                        is VoiceAnswerGradingEvent.TranscriptReady -> dispatch(RatedSessionInput.TranscriptReady(event.sanitizedTranscript))
                        is VoiceAnswerGradingEvent.Graded -> dispatch(RatedSessionInput.Graded(event.grade))
                        is VoiceAnswerGradingEvent.Failed -> {
                            logger.error { "Voice answer grading failed: ${event.reason}" }
                            dispatch(RatedSessionInput.GradingFailed(event.reason))
                        }
                    }
                }
        }
    }

    private fun stopVoiceStack() {
        listeningJob?.cancel()
        gradingJob?.cancel()
        captureGateway.stopVoiceAnswering()
        playbackGateway.stop()
        isVoiceStackStarted = false
    }

    private suspend fun isMicrophoneGranted(): Boolean =
        permissionGateway.observeStatus(AppPermission.RecordAudio).first() == PermissionStatus.Granted

    /** The coordinator never prompts: the Preview screen does. It stops voice and lets the screen end the session. */
    private fun onMicPermissionRevoked() {
        if (isMicPermissionRevoked) return
        isMicPermissionRevoked = true
        stopVoiceStack()
        eventChannel.trySend(RatedSessionEvent.MicPermissionRevoked)
    }

    private fun publish() {
        val current = state ?: return
        _sessionState.value = with(current) {
            RatedSessionStateSnapshot.Running(
                cards = remainingCards,
                masteredCount = masteredCount,
                completedCount = completedCount,
                distinctCardCount = distinctCardCount,
                currentCardRatings = currentCardRatings,
                isAnswerRevealed = isAnswerRevealed,
                playback = playback,
                round = round,
                speakingNotice = speakingNotices.firstOrNull(),
                isShortNoticeSpeaking = speakingNotices.any { it.isShort },
                isVoiceAnsweringActive = isVoiceAnsweringActive,
                voiceAnswerPauseReason = voiceAnswerPauseReason,
                isPausedAtAdvancePoint = isPausedAtAdvancePoint,
                pauseReason = pauseReason,
            )
        }
    }
}
