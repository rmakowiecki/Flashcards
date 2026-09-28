package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.RatedSessionCardRecord
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceAnswerRound
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.AdvanceAfterVoiceAnswer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.RestartCurrentCard
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AdvanceHoldReleased
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AttemptRated
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.CaptureFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.CardSkipped
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.Graded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.GradingFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeTailElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PreviousRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SilenceTimedOut
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechStarted
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TemporaryPauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TranscriptReady
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.UtteranceCaptured
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.VoiceAnsweringResumed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.VoiceStackRestarted
import javax.inject.Inject
import kotlin.random.Random

/** A [RatedSessionReducer] step: the next state and what the coordinator must do, in order. */
data class RatedSessionTransition(val state: RatedSessionState, val effects: List<RatedSessionEffect>)

/**
 * Every rule of a Rated Study Session, as a pure function: a [RatedSessionState] and a
 * [RatedSessionInput] in, the next state and an ordered list of [RatedSessionEffect]s out. It never
 * reads a clock and never touches a gateway; [RatedStudySessionCoordinator] runs the effects and
 * feeds back what they report.
 *
 * A Rating or requeue is recorded the moment it happens, but the head only moves at a queue sync.
 * A manual Rating syncs at once; a voice round waits until the notice about the head has finished,
 * so the current card is always the card on screen.
 *
 * ADR-0025 invariant: [RatedSessionEffect.OpenListeningWindow] is only ever emitted in response to
 * [RatedSessionInput.QuestionFinished], and never while a question or a notice is being spoken.
 *
 * [random] draws the re-insertion gaps (ADR-0046). Tests pass a fixed seed.
 */
class RatedSessionReducer @Inject constructor(private val random: Random) {

    /**
     * The first state: one [RatedSessionCardRecord] per card, stamped previously-mastered from
     * [previouslyMasteredCardIds] (display and Mastery Defense only).
     */
    fun seed(
        cards: List<Flashcard>,
        attemptsLimit: Int,
        partialRatingCardRequeueingEnabled: Boolean = true,
        previouslyMasteredCardIds: Set<String> = emptySet(),
        isVoiceAnsweringSession: Boolean = false,
    ): RatedSessionState {
        val state = RatedSessionState(
            queue = cards.map { card ->
                RatedSessionCardRecord(card = card, wasPreviouslyMastered = card.id in previouslyMasteredCardIds)
            },
            distinctCardCount = cards.size,
            attemptsLimit = attemptsLimit,
            partialRatingCardRequeueingEnabled = partialRatingCardRequeueingEnabled,
            isVoiceAnsweringSession = isVoiceAnsweringSession,
        )
        return state.copy(round = state.idleRound())
    }

    @Suppress("CyclomaticComplexMethod") // one branch per input, exhaustive over the sealed type.
    fun reduce(state: RatedSessionState, input: RatedSessionInput): RatedSessionTransition {
        val transition = RatedTransitionBuilder(state, random)
        with(transition) {
            when (input) {
                is AttemptRated -> onAttemptRated(input)
                AnswerRevealed -> this.state = this.state.copy(isAnswerRevealed = true)
                CardSkipped -> onCardSkipped()
                PreviousRequested -> if (acceptsCardCommand()) emit(RestartCurrentCard)
                is QuestionFinished -> onQuestionFinished(input.cardId)
                SpeechStarted -> onSpeechStarted()
                SpeechEnded -> onSpeechEnded()
                is UtteranceCaptured -> onUtteranceCaptured(input.obfuscatedWav)
                SilenceTimedOut -> onSilenceTimedOut()
                is TranscriptReady -> onTranscriptReady(input.transcript)
                is Graded -> onGraded(input.grade)
                is GradingFailed -> onGradingFailed(input.reason)
                is CaptureFailed -> onCaptureFailed()
                is NoticeFinished -> onNoticeFinished(input.notice)
                NoticeTailElapsed -> onNoticeTailElapsed()
                PlayRequested -> onPlayRequested()
                PauseRequested -> onPauseRequested()
                TemporaryPauseRequested -> onTemporaryPauseRequested()
                TemporaryPauseEnded -> if (this.state.isPausedTemporarily) onPlayRequested()
                AdvanceHoldRequested -> this.state = this.state.copy(isAdvanceHoldRequested = true)
                AdvanceHoldReleased -> onAdvanceHoldReleased()
                VoiceAnsweringResumed -> onVoiceAnsweringResumed()
                VoiceStackRestarted -> onVoiceStackRestarted()
                PlaybackEngineUnavailable -> onPlaybackEngineUnavailable()
                is PlaybackChanged -> onPlaybackChanged(input)
            }
        }
        return transition.build()
    }

    /** Ignored while a voice grade or requeue waits for its sync: the head already has its result. */
    private fun RatedTransitionBuilder.onAttemptRated(input: AttemptRated) {
        if (state.isComplete || state.isSyncPending) return
        state = recordRating(state, input.rating, random)
        syncQueue()
        if (state.isComplete) emit(SessionComplete)
    }

    /**
     * Rated "next": the presented card goes back into the queue unanswered, as after a silence.
     * Held at the auto-advance point, the card is already answered, so it moves on instead.
     */
    private fun RatedTransitionBuilder.onCardSkipped() {
        if (state.isHeldAtAdvancePoint) {
            moveOnFromAdvancePoint()
            return
        }
        if (!acceptsCardCommand()) return
        state = recordSilence(state, random).copy(round = state.idleRound())
        syncQueue()
        if (state.isPlaying) emit(AdvanceAfterVoiceAnswer)
    }

    private fun RatedTransitionBuilder.onPlayRequested() {
        state = state.copy(isPausedTemporarily = false)
        if (state.isHeldAtAdvancePoint || state.isPausedAtAdvancePoint) {
            moveOnFromAdvancePoint()
        } else if (!state.isPlaying) {
            emit(Play)
        }
    }

    /**
     * A pause always reaches the player, so it also drops an auto-resume the player has pending. A
     * pause at a hold turns it into a user pause at the auto-advance point, still on the answered card.
     */
    private fun RatedTransitionBuilder.onPauseRequested() {
        state = state.copy(
            isPausedTemporarily = false,
            isPausedAtAdvancePoint = state.isPausedAtAdvancePoint || state.isHeldAtAdvancePoint,
            isHeldAtAdvancePoint = false,
        )
        emit(PausePlayback)
    }

    /** Only pauses a session that is playing; a paused or held one stays as it is. */
    private fun RatedTransitionBuilder.onTemporaryPauseRequested() {
        if (!state.isPlaying || state.isHeldAtAdvancePoint || state.isPausedTemporarily) return
        state = state.copy(isPausedTemporarily = true)
        emit(PausePlayback)
    }

    /** Moves on only from a hold still in place; a pause, play or skip already resolved any other. */
    private fun RatedTransitionBuilder.onAdvanceHoldReleased() {
        state = state.copy(isAdvanceHoldRequested = false)
        if (state.isHeldAtAdvancePoint) moveOnFromAdvancePoint()
    }

    private fun RatedTransitionBuilder.onPlaybackChanged(input: PlaybackChanged) {
        val playback = input.playback
        val startedPlaying = playback.isPlaying && !state.isPlaying
        state = state.copy(
            isPlaying = playback.isPlaying,
            isPausedAtAdvancePoint = state.isPausedAtAdvancePoint && !startedPlaying,
            isPausedTemporarily = state.isPausedTemporarily && !startedPlaying,
        )
        if (playback.isActive && playback.phase == VoicePhase.Answer) {
            state = state.copy(isAnswerRevealed = true)
        }
    }

    /** Clears the voice-answer pause and both counters, and starts listening again from the question. */
    private fun RatedTransitionBuilder.onVoiceAnsweringResumed() {
        if (state.voiceAnswerPauseReason == null || state.pauseReason != null) return
        state = state.copy(
            voiceAnswerPauseReason = null,
            consecutiveSilenceCount = 0,
            consecutiveGradingFailureCount = 0,
            isPausedAtAdvancePoint = false,
            isPausedTemporarily = false,
        )
        state = state.copy(round = state.idleRound())
        emit(RatedSessionEffect.StartVoiceAnswering)
        if (!state.isPlaying) emit(Play)
    }

    /** The restarted player reads the presented card by itself, so no play is issued here. */
    private fun RatedTransitionBuilder.onVoiceStackRestarted() {
        if (state.pauseReason == null) return
        state = state.copy(
            pauseReason = null,
            voiceAnswerPauseReason = null,
            consecutiveSilenceCount = 0,
            consecutiveGradingFailureCount = 0,
            isPausedAtAdvancePoint = false,
        )
        state = state.copy(round = state.idleRound())
        if (state.isVoiceAnsweringSession) emit(RatedSessionEffect.StartVoiceAnswering)
    }

    /**
     * The session keeps its delivery mode (it never falls back to manual) and pauses instead. The
     * open round is dropped without a requeue, a counter or an Attempt. Notices in flight die with
     * the voice stack, so a sync still waiting on them runs now.
     */
    private fun RatedTransitionBuilder.onPlaybackEngineUnavailable() {
        if (state.pauseReason != null) return
        emit(RatedSessionEffect.CancelSilenceTimer)
        emit(RatedSessionEffect.CancelGrading)
        emit(RatedSessionEffect.StopVoiceStack)
        state = state.copy(
            pauseReason = SessionPauseReason.VoiceEngineUnavailable,
            round = VoiceAnswerRound(),
            speakingNotices = emptyList(),
            isPausedAtAdvancePoint = false,
            isHeldAtAdvancePoint = false,
            isPausedTemporarily = false,
            isPlaying = false,
        )
        if (state.isSyncPending) {
            syncQueue()
            if (state.isComplete) emit(SessionComplete)
        }
        emit(RatedSessionEffect.Emit(RatedSessionEvent.VoicePlaybackUnavailable))
    }
}

/** Mutable only while one [RatedSessionReducer.reduce] call builds its transition. */
internal class RatedTransitionBuilder(var state: RatedSessionState, val random: Random) {
    private val effects = mutableListOf<RatedSessionEffect>()

    fun emit(effect: RatedSessionEffect) {
        effects += effect
    }

    fun build(): RatedSessionTransition = RatedSessionTransition(state, effects.toList())
}

/**
 * Card commands ("next", "previous") act only at the question, or while paused at it: never
 * while the microphone is open, an answer is being graded, or a notice is being spoken.
 */
internal fun RatedTransitionBuilder.acceptsCardCommand(): Boolean = with(state) {
    !isComplete &&
        (round.phase == VoiceAnswerPhase.Idle || round.phase == VoiceAnswerPhase.WaitingForQuestion) &&
        speakingNotices.isEmpty() &&
        !isSyncPending
}

/** Moves the head as the pending move says, hides the answer and hands the queue to the voice player. */
internal fun RatedTransitionBuilder.syncQueue() {
    state = applyPendingMove(state).copy(isAnswerRevealed = false)
    emit(RatedSessionEffect.SyncQueue(state.remainingCards))
}

/**
 * From the auto-advance point: the queue sync still pending runs, then the next question is read,
 * or the session ends when the queue is complete.
 */
internal fun RatedTransitionBuilder.moveOnFromAdvancePoint() {
    state = state.copy(isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false, isPausedTemporarily = false)
    if (state.isSyncPending) syncQueue()
    state = state.copy(round = state.idleRound())
    emit(if (state.isComplete) SessionComplete else AdvanceAfterVoiceAnswer)
}

/** The round between answers: waiting for the next question while voice answering is on, idle otherwise. */
internal fun RatedSessionState.idleRound(): VoiceAnswerRound =
    if (isVoiceAnsweringActive && !isComplete) VoiceAnswerRound(phase = VoiceAnswerPhase.WaitingForQuestion) else VoiceAnswerRound()
