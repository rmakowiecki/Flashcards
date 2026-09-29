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
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import java.time.Clock
import javax.inject.Inject
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
 * With read-aloud on, the player runs its own question-pause-answer-next loop; this runs every
 * transport command through the reducer and, while [holdAdvance] is in place, closes the player's
 * gate at the auto-advance point. A read-aloud session never falls back to tap-through: an
 * unavailable voice engine pauses it, and [play] restarts the voice stack.
 */
@Suppress("TooManyFunctions") // one command per session action.
class FastStudySessionCoordinator @Inject constructor(
    private val getSessionStartData: GetSessionStartDataUseCase,
    private val playbackGateway: StudyVoicePlaybackGateway,
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
    private var isObservingVoiceStack = false
    private var hasEnded = false
    private var pushedTransportCommands: Set<TransportCommandType>? = null

    // When the presented card last started or restarted, for the rewind threshold.
    private var presentedCardStartedAt: ComparableTimeMark = timeSource.markNow()
    private var presentedCardIndex = NO_CARD_INDEX

    /**
     * The session-start progress read. Fast has no mastery concept: only a card's absence here
     * matters, as the new-card scoring bonus, and never reaches a persisted document. A failed read
     * leaves it empty rather than blocking the session.
     */
    internal var priorProgressByCardId: Map<String, CardProgressEntry> = emptyMap()
        private set

    // ADR-0047's snapshot rule, as in the Rated coordinator.
    private var xpConfig = XpConfig()

    fun start(scope: CoroutineScope, setup: FastSessionSetup) {
        check(this.scope == null) { "A coordinator runs one session" }
        this.scope = scope
        this.setup = setup
        voiceSettings = setup.voiceSettings
        scope.launch { load() }
    }

    /** Synchronous: `viewModelScope` is already cancelled when `onCleared` runs. */
    fun stop() {
        playbackGateway.stop()
    }

    /** With read-aloud on the player reads the answer and reports it revealed, which marks the card Studied. */
    fun revealAnswer() {
        val current = state ?: return
        if (playback.isActive) {
            playbackGateway.showAnswer()
        } else {
            current.cards.getOrNull(current.currentIndex)?.let { card -> dispatch(FastSessionInput.AnswerRevealed(card.id)) }
        }
    }

    /** The manual advance, with read-aloud off. On the last card it ends the session. */
    fun nextCard() {
        if (!playback.isActive) dispatch(FastSessionInput.NextCardRequested)
    }

    /** Also the resume after an engine failure: the voice stack starts again at the presented card. */
    fun play() {
        dispatch(FastSessionInput.PlayRequested)
    }

    fun pause() {
        dispatch(FastSessionInput.PauseRequested)
    }

    /**
     * The read-aloud Next, in-app or from outside the app: at a question it reveals that card's
     * answer, at an answer it moves on. At the last card's answer it does nothing; the session ends
     * when the player finishes reading it.
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

    /** Moves on and plays only when still held at the auto-advance point; otherwise it only drops the request. */
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
                xpConfig = xpConfig,
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
        xpConfig = sessionStartData.xpConfig
        val cardsById = flashcards.associateBy { it.id }
        val sessionCards = setup.cardIds.mapNotNull(cardsById::get)
        state = reducer.seed(sessionCards)
        publishPresentationState()
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
        if (current.isAdvanceHoldRequested) playbackGateway.setAdvanceGate(closed = true)
    }

    private fun observeVoiceStack() {
        if (isObservingVoiceStack) return
        isObservingVoiceStack = true
        val scope = requireNotNull(scope)
        scope.launch {
            playbackGateway.state.collect { playbackState ->
                playback = playbackState
                markPresentedCard(playbackState)
                dispatch(FastSessionInput.PlaybackChanged(playbackState))
            }
        }
        scope.launch { playbackGateway.playbackEvents.collect(::onPlaybackEvent) }
    }

    private fun markPresentedCard(playbackState: VoicePlaybackState) {
        if (!playbackState.isActive) {
            presentedCardIndex = NO_CARD_INDEX
        } else if (playbackState.currentIndex != presentedCardIndex) {
            presentedCardIndex = playbackState.currentIndex
            presentedCardStartedAt = timeSource.markNow()
        }
    }

    private fun onPlaybackEvent(event: PlaybackEvent) {
        when (event) {
            is PlaybackEvent.ExternalCommand -> onExternalCommand(event.command)
            PlaybackEvent.EngineUnavailable -> dispatch(FastSessionInput.PlaybackEngineUnavailable)
            PlaybackEvent.EndReached -> dispatch(FastSessionInput.PlaybackEndReached)
            PlaybackEvent.AdvanceGateReached -> dispatch(FastSessionInput.AdvanceGateReached)
            is PlaybackEvent.AnswerRevealed -> dispatch(FastSessionInput.AnswerRevealed(event.cardId))
            // Fast reads answers and speaks no notices.
            is PlaybackEvent.QuestionFinished, is PlaybackEvent.NoticeFinished -> Unit
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
        presentedCardStartedAt.elapsedNow() >= REWIND_THRESHOLD || playback.currentIndex == 0

    /** Returns whether [input] changed the session: its state, or anything the coordinator had to do. */
    private fun dispatch(input: FastSessionInput): Boolean {
        val current = state ?: return false
        val transition = reducer.reduce(current, input)
        state = transition.state
        transition.effects.forEach(::run)
        publishPresentationState()
        return transition.state != current || transition.effects.isNotEmpty()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per effect, exhaustive over the sealed type.
    private fun run(effect: FastSessionEffect) {
        when (effect) {
            FastSessionEffect.Play -> playbackGateway.play()
            FastSessionEffect.Pause -> playbackGateway.pause()
            FastSessionEffect.ShowAnswer -> playbackGateway.showAnswer()
            FastSessionEffect.MoveToNextCard -> playbackGateway.moveToNextCard()
            FastSessionEffect.MoveToPreviousCard -> playbackGateway.moveToPreviousCard()
            FastSessionEffect.RestartCurrentCard -> {
                playbackGateway.restartCurrentCard()
                presentedCardStartedAt = timeSource.markNow()
            }
            is FastSessionEffect.JumpTo -> playbackGateway.jumpTo(effect.index)
            is FastSessionEffect.SetAdvanceGate -> playbackGateway.setAdvanceGate(effect.closed)
            is FastSessionEffect.RestartVoiceStack -> {
                playbackGateway.stop()
                startVoiceStack(startIndex = effect.startIndex)
            }
            FastSessionEffect.StopVoiceStack -> {
                logger.warn { "Voice engine unavailable, Fast session paused" }
                playback = VoicePlaybackState()
                playbackGateway.stop()
            }
            is FastSessionEffect.Emit -> eventChannel.trySend(effect.event)
            // The last card's answer is read in full before this fires, never when it merely started.
            FastSessionEffect.SessionComplete -> end(abandoned = false)
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
    private fun publishPresentationState() {
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
            isReadAloudNextAvailable = current.isReadAloudNextAvailable,
            playback = playback,
            pauseReason = current.pauseReason,
            isHeldAtAdvancePoint = current.isHeldAtAdvancePoint,
            availableTransportCommands = availableCommands,
        )
    }

    private companion object {
        const val NO_CARD_INDEX = -1
    }
}
