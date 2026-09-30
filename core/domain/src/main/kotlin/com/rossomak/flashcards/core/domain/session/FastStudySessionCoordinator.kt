package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.DomainLogger
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.AudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelBlipTimer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReadAloudPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReleaseLinger
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentAnswer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentQuestion
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.RestartVoiceStack
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartAdvancePause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartBlipTimer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartQuestionPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartReleaseLinger
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StopVoiceStack
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import java.time.Clock
import javax.inject.Inject
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Runs one Fast Study Session: loads its cards, holds the [FastSessionReducer] state, maps every
 * transport command (in-app or from outside the app) onto the voice player, and owns the Studied
 * set, the session clock and the result
 * ([ADR-0054](../../../../../../../docs/adr/0054-study-session-rules-in-domain-coordinators.md)).
 *
 * With read-aloud on, this owns the card position and the question, pause, answer, pause, next card
 * loop: the player presents one part of one card when told to and reports when it was read, and the
 * two pauses between parts are timers here. While [holdAdvance] is in place, the loop stops on the
 * presented card at the auto-advance point. A read-aloud session never falls back to tap-through: an
 * unavailable voice engine pauses it, and [play] restarts the voice stack.
 *
 * What other apps' audio does to the session ([AudioInterruptionGateway]) is decided by the reducer
 * from each signal and the time this coordinator stamps it with; this only runs the blip timer and
 * writes the log lines.
 */
@Suppress("TooManyFunctions") // one command per session action.
class FastStudySessionCoordinator @Inject constructor(
    private val getSessionStartData: GetSessionStartDataUseCase,
    private val playbackGateway: StudyVoicePlaybackGateway,
    private val interruptionGateway: AudioInterruptionGateway,
    private val reducer: FastSessionReducer,
    clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks,
    private val logger: DomainLogger,
) {
    private val _sessionState = MutableStateFlow<FastSessionStateSnapshot>(FastSessionStateSnapshot.Loading)
    val sessionState: StateFlow<FastSessionStateSnapshot> = _sessionState.asStateFlow()

    private val eventChannel = Channel<FastSessionEvent>(Channel.UNLIMITED)
    val events: Flow<FastSessionEvent> = eventChannel.receiveAsFlow()

    private val timekeeper = SessionTimekeeper(clock)

    private var scope: CoroutineScope? = null
    private lateinit var setup: FastSessionSetup
    private lateinit var voiceSettings: VoiceSettings

    // null until the cards load; inputs arriving before then have nothing to act on.
    private var state: FastSessionState? = null
    private var playback = VoicePlaybackState()

    private val pendingInputs = ArrayDeque<FastSessionInput>()
    private var isDispatching = false

    private var isObservingVoiceStack = false
    private var hasEnded = false
    private var pushedTransportCommands: Set<TransportCommandType>? = null
    private var readAloudPauseJob: Job? = null
    private val releaseLingerTimer = ReleaseLingerTimer { dispatch(FastSessionInput.ReleaseLingerElapsed) }
    private val interruptionTimers = InterruptionTimers(onBlipElapsed = { dispatch(FastSessionInput.BlipElapsed) })

    // When the presented card last started or restarted, for the rewind threshold.
    private var presentedCardStartedAt: ComparableTimeMark = timeSource.markNow()

    /**
     * The session-start progress read. Fast has no mastery concept: only a card's absence here
     * matters, as the new-card scoring bonus, and never reaches a persisted document. A failed read
     * leaves it empty rather than blocking the session.
     */
    internal var priorProgressByCardId: Map<String, CardProgressEntry> = emptyMap()
        private set

    fun start(scope: CoroutineScope, setup: FastSessionSetup) {
        check(this.scope == null) { "A coordinator runs one session" }
        this.scope = scope
        this.setup = setup
        voiceSettings = setup.voiceSettings
        scope.launch { load() }
    }

    /** Synchronous: `viewModelScope` is already cancelled when `onCleared` runs. */
    fun stop() {
        readAloudPauseJob?.cancel()
        releaseLingerTimer.cancel()
        interruptionTimers.cancelAll()
        playbackGateway.stop()
    }

    /** The manual reveal: only a session without read-aloud offers it. */
    fun revealAnswer() {
        val current = state ?: return
        current.cards.getOrNull(current.currentIndex)?.let { card -> dispatch(FastSessionInput.AnswerRevealed(card.id)) }
    }

    /** Also the resume after an engine failure: the voice stack starts again at the presented card. */
    fun play() {
        dispatch(FastSessionInput.PlayRequested)
    }

    fun pause() {
        dispatch(FastSessionInput.PauseRequested)
    }

    /**
     * Next, in-app or from outside the app. With read-aloud on, at a question it reveals that card's
     * answer, at an answer it moves on; at the last card's answer it does nothing, and the session
     * ends after the pause that follows reading it. Without read-aloud it advances once the answer
     * shows, and on the last card it ends the session.
     */
    fun next() {
        dispatch(FastSessionInput.NextRequested)
    }

    /** Restarts the card once it has played for the rewind threshold, or on the first card; goes back one otherwise. */
    fun previous() {
        dispatch(FastSessionInput.PreviousRequested(restartsCard = isPastRewindThreshold()))
    }

    /**
     * Keeps read-aloud going, but stops it on the presented card at the auto-advance point until
     * [releaseAdvance]. A hold is not a pause: a pause, play or card change resolves it.
     */
    fun holdAdvance() {
        dispatch(FastSessionInput.AdvanceHoldRequested)
    }

    /**
     * Moves on and plays only when still held at the auto-advance point, after the release linger
     * keeps the held card on screen a moment longer; otherwise it only drops the request.
     */
    fun releaseAdvance() {
        dispatch(FastSessionInput.AdvanceHoldReleased)
    }

    /** Pauses a playing session until [endTemporaryPause]; a user pause or play in between replaces it. */
    fun pauseTemporarily() {
        dispatch(FastSessionInput.TemporaryPauseRequested)
    }

    /** Plays again only when the session is still paused by [pauseTemporarily]. */
    fun endTemporaryPause() {
        dispatch(FastSessionInput.TemporaryPauseEnded)
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
     * Seals the result exactly once and reports it as [FastSessionEvent.SessionEnded]. One
     * [FlashcardResult.Fast] per Studied card, in first-seen order; leaving before the cards load
     * seals empty card results with zero duration. Playback stops here, not at `onCleared`, so nothing
     * is read while the screen navigates away.
     */
    fun end(abandoned: Boolean) {
        if (hasEnded) return
        hasEnded = true
        stop()
        val cardResults = state?.let(::sealFastCardResults) ?: emptyList()
        val result = timekeeper.seal { at ->
            SessionResult.Fast(
                id = timekeeper.sessionId,
                startedAt = timekeeper.startedAt ?: at,
                durationSeconds = 0, // overwritten by the seal
                abandoned = abandoned,
                categoryId = setup.categoryId,
                categoryName = setup.categoryName,
                subcategoryIds = setup.subcategoryIds,
                subcategoryNames = setup.subcategoryNames,
                cardResults = cardResults,
                // As in the Rated coordinator: only the UTC offset is a real value here.
                studyDate = "",
                studyDateUtcOffsetMinutes = timekeeper.utcOffsetMinutes,
                dailyGoalMinutes = 0,
            )
        }
        eventChannel.trySend(FastSessionEvent.SessionEnded(result))
    }

    private suspend fun load() {
        val sessionStartData = getSessionStartData(setup.subcategoryIds)
        val flashcards = sessionStartData.flashcardsResult.getOrElse {
            _sessionState.value = FastSessionStateSnapshot.LoadFailed
            return
        }
        priorProgressByCardId = sessionStartData.priorProgressByCardId
        val cardsById = flashcards.associateBy { it.id }
        val sessionCards = setup.cardIds.mapNotNull(cardsById::get)
        state = reducer.seed(sessionCards, isReadAloudSession = setup.readAloudEnabled)
        publish()
        if (sessionCards.isEmpty()) return
        // Read-aloud off is a tap-through session and never starts text-to-speech.
        if (setup.readAloudEnabled) startVoiceStack(startIndex = 0)
        timekeeper.start()
    }

    private fun startVoiceStack(startIndex: Int) {
        val current = state ?: return
        observeVoiceStack()
        playbackGateway.start(
            cards = current.cards,
            startIndex = startIndex,
            sessionTitle = setup.sessionTitle,
            isVoiceAnsweringSession = false,
        )
        playbackGateway.setSpeechRate(voiceSettings.speechRate)
        playbackGateway.setVoice(voiceSettings.voiceId)
        presentedCardStartedAt = timeSource.markNow()
    }

    private fun observeVoiceStack() {
        if (isObservingVoiceStack) return
        isObservingVoiceStack = true
        val scope = requireNotNull(scope)
        scope.launch {
            playbackGateway.state.collect { playbackState ->
                playback = playbackState
                dispatch(FastSessionInput.PlaybackChanged(playbackState))
            }
        }
        scope.launch { playbackGateway.playbackEvents.collect(::onPlaybackEvent) }
        scope.launch {
            interruptionGateway.signals.collect { signal ->
                dispatch(FastSessionInput.AudioEnvironmentChanged(signal, timeSource.markNow()))
            }
        }
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.ExternalCommand -> onExternalCommand(event.command)
            PlaybackEvent.EngineUnavailable -> dispatch(FastSessionInput.PlaybackEngineUnavailable)
            is PlaybackEvent.AnswerRevealed -> dispatch(FastSessionInput.AnswerRevealed(event.cardId))
            is PlaybackEvent.QuestionFinished -> dispatch(FastSessionInput.QuestionFinished(event.cardId))
            is PlaybackEvent.AnswerFinished -> dispatch(FastSessionInput.AnswerFinished(event.cardId))
            // Fast speaks no notices.
            is PlaybackEvent.NoticeFinished -> Unit
        }
    }

    /**
     * Applied exactly like the matching in-app command. A controller's stop only pauses. A command
     * that changed the session is then reported, so the screen can react to it. An ended session
     * ignores them.
     */
    private fun onExternalCommand(command: TransportCommand) {
        if (hasEnded) return
        val input = when (command) {
            TransportCommand.Play -> FastSessionInput.PlayRequested
            TransportCommand.Pause, TransportCommand.Stop -> FastSessionInput.PauseRequested
            TransportCommand.Next -> FastSessionInput.NextRequested
            TransportCommand.Previous -> FastSessionInput.PreviousRequested(restartsCard = isPastRewindThreshold())
            TransportCommand.PreviousCard -> FastSessionInput.PreviousRequested(restartsCard = false)
            is TransportCommand.JumpTo -> FastSessionInput.JumpRequested(command.index)
        }
        if (dispatch(input)) eventChannel.trySend(FastSessionEvent.ExternalTransportCommand(command))
    }

    private fun isPastRewindThreshold(): Boolean =
        presentedCardStartedAt.elapsedNow() >= REWIND_THRESHOLD || state?.currentIndex == 0

    /**
     * Runs one input at a time, in arrival order, even when an effect feeds another input back.
     * Returns whether the inputs run changed the session: its state, or anything the coordinator had
     * to do. An input queued behind one already running reports `false`.
     */
    private fun dispatch(input: FastSessionInput): Boolean {
        pendingInputs.addLast(input)
        if (isDispatching) return false
        isDispatching = true
        val stateBefore = state
        var hasEffects = false
        try {
            while (pendingInputs.isNotEmpty()) {
                val next = pendingInputs.removeFirst()
                val current = state ?: continue
                val transition = reducer.reduce(current, next)
                state = transition.state
                hasEffects = hasEffects || transition.effects.isNotEmpty()
                transition.effects.forEach(::run)
            }
        } finally {
            isDispatching = false
        }
        publish()
        return hasEffects || state != stateBefore
    }

    @Suppress("CyclomaticComplexMethod") // one branch per effect, exhaustive over the sealed type.
    private fun run(effect: FastSessionEffect) {
        when (effect) {
            Play -> playbackGateway.play()
            PausePlayback -> playbackGateway.pause()
            // Every question presented starts or restarts the card, for the rewind threshold.
            is PresentQuestion -> {
                presentedCardStartedAt = timeSource.markNow()
                playbackGateway.presentQuestion(effect.index)
            }
            is PresentAnswer -> playbackGateway.presentAnswer(effect.index)
            StartQuestionPause -> startReadAloudPause(QUESTION_TO_ANSWER_PAUSE, FastSessionInput.QuestionPauseElapsed)
            StartAdvancePause -> startReadAloudPause(ANSWER_TO_NEXT_PAUSE, FastSessionInput.AdvancePauseElapsed)
            CancelReadAloudPause -> readAloudPauseJob?.cancel()
            StartReleaseLinger -> releaseLingerTimer.start(requireNotNull(scope))
            CancelReleaseLinger -> releaseLingerTimer.cancel()
            is RestartVoiceStack -> {
                playbackGateway.stop()
                startVoiceStack(startIndex = effect.startIndex)
            }
            // The reducer emits CancelReadAloudPause first, so a running pause is already stopped.
            StopVoiceStack -> {
                logger.warn { "Voice engine unavailable, Fast session paused" }
                playback = VoicePlaybackState()
                playbackGateway.stop()
            }
            is Emit -> eventChannel.trySend(effect.event)
            // The last card's answer is read in full before this fires, never when it merely started.
            SessionComplete -> end(abandoned = false)
            is StartBlipTimer -> interruptionTimers.startBlip(requireNotNull(scope), effect.duration)
            CancelBlipTimer -> interruptionTimers.cancelBlip()
        }
    }

    private fun startReadAloudPause(duration: Duration, elapsed: FastSessionInput) {
        readAloudPauseJob?.cancel()
        readAloudPauseJob = requireNotNull(scope).launch {
            delay(duration)
            dispatch(elapsed)
        }
    }

    private fun sealFastCardResults(state: FastSessionState): List<FlashcardResult.Fast> {
        val cardsById = state.cards.associateBy { it.id }
        return state.seenCardIds.mapNotNull { cardId ->
            cardsById[cardId]?.let { card ->
                FlashcardResult.Fast(cardId = card.id, subcategoryId = card.subcategoryId, state = FlashcardStudyProgressState.Seen)
            }
        }
    }

    /** The system transport controls get the same commands as the screen; unchanged ones are not sent again. */
    private fun publish() {
        val current = state ?: return
        val availableCommands = current.availableTransportCommands
        if (setup.readAloudEnabled && availableCommands != pushedTransportCommands) {
            pushedTransportCommands = availableCommands
            playbackGateway.setAvailableCommands(availableCommands)
        }
        _sessionState.value = FastSessionStateSnapshot.Running(
            cards = current.cards,
            currentIndex = current.currentIndex,
            isAnswerRevealed = current.isAnswerRevealed,
            playback = playback,
            pauseReason = current.pauseReason,
            isHeldAtAdvancePoint = current.isHeldAtAdvancePoint,
            availableTransportCommands = availableCommands,
        )
    }
}
