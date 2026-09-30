package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.DomainLogger
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.EpisodeChange
import com.rossomak.flashcards.core.domain.model.EpisodeEnd
import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.InterruptionTier
import com.rossomak.flashcards.core.domain.model.ReadAloudStep
import com.rossomak.flashcards.core.domain.model.ReadAloudStep.AdvancePause
import com.rossomak.flashcards.core.domain.model.ReadAloudStep.Answer
import com.rossomak.flashcards.core.domain.model.ReadAloudStep.Question
import com.rossomak.flashcards.core.domain.model.ReadAloudStep.QuestionPause
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelBlipTimer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReadAloudPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReleaseLinger
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentAnswer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentQuestion
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartAdvancePause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartBlipTimer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartQuestionPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartReleaseLinger
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldReleased
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvancePauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AudioEnvironmentChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.BlipElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.JumpRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PreviousRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionPauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.ReleaseLingerElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseRequested
import javax.inject.Inject

/** A [FastSessionReducer] step: the next state and what the coordinator must do, in order. */
data class FastSessionTransition(val state: FastSessionState, val effects: List<FastSessionEffect>)

/**
 * Every rule of a Fast Study Session, as a pure function. A card becomes Studied once its answer
 * shows, read aloud or revealed by hand. With read-aloud on, this runs the question, pause, answer,
 * pause, next card loop: it tells the player which part of which card to present, starts the two
 * pauses, and decides at the auto-advance point (the end of the pause after an answer) whether to
 * move on or stop there while a hold is requested. It also decides every transport command and who
 * paused the session. [logger] only records audio interruption decisions; it never affects the
 * state or the effects.
 */
@Suppress("TooManyFunctions") // one handler per input.
class FastSessionReducer @Inject constructor(private val logger: DomainLogger) {

    fun seed(cards: List<Flashcard>, isReadAloudSession: Boolean): FastSessionState =
        FastSessionState(cards = cards, isReadAloudSession = isReadAloudSession)

    fun reduce(state: FastSessionState, input: FastSessionInput): FastSessionTransition {
        val transition = dropStaleReleaseLinger(reduceInput(state, input))
        return stripPlayDuringCall(withdrawResumeOnPlay(escalateLongBlip(transition)))
    }

    @Suppress("CyclomaticComplexMethod") // one branch per input, exhaustive over the sealed type.
    private fun reduceInput(state: FastSessionState, input: FastSessionInput): FastSessionTransition = when (input) {
        is AnswerRevealed -> onAnswerRevealed(state, input.cardId)
        is PlaybackChanged -> onPlaybackChanged(state, input.playback)
        PlaybackEngineUnavailable -> onPlaybackEngineUnavailable(state)
        is QuestionFinished -> onPartFinished(state, input.cardId, Question)
        is AnswerFinished -> onPartFinished(state, input.cardId, Answer)
        QuestionPauseElapsed -> onQuestionPauseElapsed(state)
        AdvancePauseElapsed -> onAdvancePoint(state)
        PlayRequested -> onUserPlayRequested(state)
        PauseRequested -> onUserPauseRequested(state)
        NextRequested -> if (state.isReadAloudSession) onReadAloudNextRequested(state) else onManualNextRequested(state)
        is PreviousRequested -> onCardChangeRequested(
            state = state,
            targetIndex = if (input.restartsCard) state.currentIndex else state.currentIndex - 1,
            isIgnored = !input.restartsCard && state.currentIndex == 0,
        )
        is JumpRequested -> {
            val targetIndex = input.index.coerceIn(0, maxOf(0, state.cards.lastIndex))
            onCardChangeRequested(state, targetIndex, isIgnored = targetIndex == state.currentIndex)
        }
        TemporaryPauseRequested -> onTemporaryPauseRequested(state)
        TemporaryPauseEnded -> onTemporaryPauseEnded(state)
        AdvanceHoldRequested -> onAdvanceHoldRequested(state)
        AdvanceHoldReleased -> onAdvanceHoldReleased(state)
        ReleaseLingerElapsed -> if (state.isReleaseLingering) moveOn(state.copy(isReleaseLingering = false)) else FastSessionTransition(state, emptyList())
        is AudioEnvironmentChanged -> onAudioEnvironmentChanged(state, input)
        BlipElapsed -> FastSessionTransition(state.copy(episode = state.episode.markBlipLong()), emptyList())
    }

    /**
     * Revealing an answer is what makes a card Studied. The player reports the card it presented,
     * which can already be behind the presented one after a quick skip; it still counts.
     */
    private fun onAnswerRevealed(state: FastSessionState, cardId: String): FastSessionTransition {
        val seen = state.markSeen(cardId)
        return FastSessionTransition(if (state.isPresented(cardId)) seen.copy(isAnswerRevealed = true) else seen, emptyList())
    }

    /** The tap-through Next. Ignored until the answer shows, so the last card is always Studied before this ends the session. */
    private fun onManualNextRequested(state: FastSessionState): FastSessionTransition =
        if (!state.isAnswerRevealed) {
            FastSessionTransition(state, emptyList())
        } else if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(state, listOf(SessionComplete))
        } else {
            FastSessionTransition(state.copy(currentIndex = state.currentIndex + 1, isAnswerRevealed = false), emptyList())
        }

    /**
     * Only whether the player plays. The player follows the coordinator's orders and starts and
     * stops by nothing else, apart from a failed utterance: the player stopping by itself inside a
     * pause stops the pause, which the next resume then skips.
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state.copy(isPlaying = false), emptyList())
        val stoppedPlaying = !playback.isPlaying && state.isPlaying
        val updated = state.copy(isPlaying = playback.isPlaying)
        return if (stoppedPlaying && state.readAloudStep.isPause) {
            FastSessionTransition(updated, listOf(CancelReadAloudPause))
        } else {
            FastSessionTransition(updated, emptyList())
        }
    }

    /** A part read in full starts the pause after it. A stale report (another card or part, or a paused session) does nothing. */
    private fun onPartFinished(state: FastSessionState, cardId: String, step: ReadAloudStep): FastSessionTransition {
        if (state.readAloudStep != step || !state.isPresented(cardId) || !state.isReading) return FastSessionTransition(state, emptyList())
        return if (step == Question) {
            FastSessionTransition(state.copy(readAloudStep = QuestionPause), listOf(StartQuestionPause))
        } else {
            FastSessionTransition(state.copy(readAloudStep = AdvancePause), listOf(StartAdvancePause))
        }
    }

    /** Play also restarts the voice stack after an engine failure; see [resume] for the rest. */
    private fun onPlayRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.VoiceEngineUnavailable -> FastSessionTransition(
            state.copy(pauseReason = null, readAloudStep = Question, isAnswerRevealed = false),
            listOf(FastSessionEffect.RestartVoiceStack(startIndex = state.currentIndex)),
        )
        state.isReading -> FastSessionTransition(state, emptyList())
        else -> resume(state)
    }

    /**
     * Every resume goes through here: play, a temporary pause ending, and the player playing again by
     * itself. Inside a read-aloud pause it goes on to the next step, and the rest of the pause is
     * dropped: the answer after the question pause, the next card (or the end) after the advance
     * pause. Inside a part, the player reads that part again from its start.
     */
    private fun resume(state: FastSessionState): FastSessionTransition {
        val plays = !state.isReading
        val resumed = state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        return when (state.readAloudStep) {
            QuestionPause -> presentAnswer(resumed, cancelsPause = false, plays = plays)
            AdvancePause -> moveOn(state)
            Question, Answer -> FastSessionTransition(resumed, if (plays) listOf(Play) else emptyList())
        }
    }

    /**
     * A pause always reaches the player, so it also drops an auto-resume the player has pending, and
     * stops a running read-aloud pause. A pause at a hold turns it into a user pause at the
     * auto-advance point.
     */
    private fun onPauseRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.VoiceEngineUnavailable -> FastSessionTransition(state, emptyList())
        state.isHeldAtAdvancePoint -> FastSessionTransition(
            state.copy(pauseReason = FastPauseReason.User, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = true),
            listOf(PausePlayback),
        )
        else -> FastSessionTransition(state.copy(pauseReason = FastPauseReason.User), state.cancelPauseEffects() + PausePlayback)
    }

    /**
     * The read-aloud Next. At a question, or in the pause after it, it presents that card's answer; at an answer, in the
     * pause after it, or held at the advance point, it moves on.
     */
    private fun onReadAloudNextRequested(state: FastSessionState): FastSessionTransition = when {
        !state.isReadAloudNextAvailable -> FastSessionTransition(state, emptyList())
        state.isHeldAtAdvancePoint -> moveOn(state)
        state.readAloudStep == Question || state.readAloudStep == QuestionPause ->
            presentAnswer(state, cancelsPause = true, plays = false)
        else -> presentQuestion(state.copy(isPausedAtAdvancePoint = false), state.currentIndex + 1, cancelsPause = true, plays = false)
    }

    /** The pause between the question and the answer is over; a stale timer, or a paused session, does nothing. */
    private fun onQuestionPauseElapsed(state: FastSessionState): FastSessionTransition =
        if (state.readAloudStep == QuestionPause && state.isReading) {
            presentAnswer(state, cancelsPause = false, plays = false)
        } else {
            FastSessionTransition(state, emptyList())
        }

    /** Only ends the pause it started: a user pause or play in between replaced it. */
    private fun onTemporaryPauseEnded(state: FastSessionState): FastSessionTransition =
        if (state.pauseReason == FastPauseReason.Temporary) resume(state) else FastSessionTransition(state, emptyList())

    /** A card change ends a hold, and plays on as the hold would have. A user pause stays paused. */
    private fun onCardChangeRequested(state: FastSessionState, targetIndex: Int, isIgnored: Boolean): FastSessionTransition {
        if (isIgnored) return FastSessionTransition(state, emptyList())
        return presentQuestion(
            state = state.copy(isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false),
            index = targetIndex,
            cancelsPause = true,
            plays = state.isHeldAtAdvancePoint,
        )
    }

    /**
     * Only pauses a session that is playing; a paused one stays paused by whoever paused it. A dialog
     * that opens while an interruption holds the session takes over the resume: the dialog closing
     * plays again, not the end of the interruption.
     */
    private fun onTemporaryPauseRequested(state: FastSessionState): FastSessionTransition =
        if (state.isHeldByResumableInterruption()) {
            FastSessionTransition(state.copy(pauseReason = FastPauseReason.Temporary, episode = state.episode.cancelResume()), emptyList())
        } else if (state.pauseReason == null && state.isPlaying) {
            FastSessionTransition(state.copy(pauseReason = FastPauseReason.Temporary), state.cancelPauseEffects() + PausePlayback)
        } else {
            FastSessionTransition(state, emptyList())
        }

    /** A hold requested again while the release lingers keeps the session held on the same card. */
    private fun onAdvanceHoldRequested(state: FastSessionState): FastSessionTransition = FastSessionTransition(
        state.copy(isAdvanceHoldRequested = true, isReleaseLingering = false),
        if (state.isReleaseLingering) listOf(CancelReleaseLinger) else emptyList(),
    )

    /**
     * A hold still in place moves on once the release linger has run; a pause, play or card change
     * already resolved any other.
     */
    private fun onAdvanceHoldReleased(state: FastSessionState): FastSessionTransition {
        val released = state.copy(isAdvanceHoldRequested = false)
        if (!state.isHeldAtAdvancePoint || state.isReleaseLingering) return FastSessionTransition(released, emptyList())
        return FastSessionTransition(released.copy(isReleaseLingering = true), listOf(StartReleaseLinger))
    }

    /** Whatever ended the hold during the release linger, the linger has nothing left to move on from. */
    private fun dropStaleReleaseLinger(transition: FastSessionTransition): FastSessionTransition = with(transition) {
        if (!state.isReleaseLingering || state.isHeldAtAdvancePoint) return this
        FastSessionTransition(state.copy(isReleaseLingering = false), effects + CancelReleaseLinger)
    }

    /**
     * The pause after an answer is over. A pause that got there first wins; a requested hold stops
     * the session on the presented card, paused; otherwise it moves on.
     */
    private fun onAdvancePoint(state: FastSessionState): FastSessionTransition = when {
        state.readAloudStep != AdvancePause || state.isHeldAtAdvancePoint -> FastSessionTransition(state, emptyList())
        state.pauseReason != null -> FastSessionTransition(state.copy(isPausedAtAdvancePoint = true), emptyList())
        state.isAdvanceHoldRequested -> FastSessionTransition(state.copy(isHeldAtAdvancePoint = true), listOf(PausePlayback))
        else -> moveOn(state)
    }

    /** From the auto-advance point: the next card plays, or the session ends after the last one. */
    private fun moveOn(state: FastSessionState): FastSessionTransition {
        val moved = state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        return if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(moved, listOf(SessionComplete))
        } else {
            presentQuestion(moved, state.currentIndex + 1, cancelsPause = false, plays = !state.isReading)
        }
    }

    private fun presentQuestion(state: FastSessionState, index: Int, cancelsPause: Boolean, plays: Boolean): FastSessionTransition {
        val effects = buildList {
            if (cancelsPause) addAll(state.cancelPauseEffects())
            add(PresentQuestion(index))
            if (plays) add(Play)
        }
        return FastSessionTransition(state.copy(currentIndex = index, readAloudStep = Question, isAnswerRevealed = false), effects)
    }

    private fun presentAnswer(state: FastSessionState, cancelsPause: Boolean, plays: Boolean): FastSessionTransition {
        val effects = buildList {
            if (cancelsPause) addAll(state.cancelPauseEffects())
            add(PresentAnswer(state.currentIndex))
            if (plays) add(Play)
        }
        return FastSessionTransition(state.copy(readAloudStep = Answer, isAnswerRevealed = true), effects)
    }

    private fun onPlaybackEngineUnavailable(state: FastSessionState): FastSessionTransition {
        if (state.pauseReason == FastPauseReason.VoiceEngineUnavailable) return FastSessionTransition(state, emptyList())
        return FastSessionTransition(
            state.copy(
                pauseReason = FastPauseReason.VoiceEngineUnavailable,
                isPlaying = false,
                isHeldAtAdvancePoint = false,
                isPausedAtAdvancePoint = false,
            ),
            state.cancelPauseEffects() + listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable)),
        )
    }

    private fun FastSessionState.isPresented(cardId: String): Boolean = cards.getOrNull(currentIndex)?.id == cardId

    private fun FastSessionState.cancelPauseEffects(): List<FastSessionEffect> =
        if (readAloudStep.isPause) listOf(CancelReadAloudPause) else emptyList()

    private fun FastSessionState.markSeen(cardId: String): FastSessionState =
        if (cardId in seenCardIds || cards.none { it.id == cardId }) this else copy(seenCardIds = seenCardIds + cardId)

    // ---- Audio interruptions: what other apps' audio does to read-aloud, by severity.

    /** Play from the user: nothing starts while a call rings or runs; any other interruption gives way, and the session plays now. */
    private fun onUserPlayRequested(state: FastSessionState): FastSessionTransition {
        if (InterruptionPolicy.ignoresPlay(state.episode, hasSomethingToPlay = !state.isReading)) {
            return FastSessionTransition(state, listOf(FastSessionEffect.Emit(FastSessionEvent.PlayIgnoredDuringCall)))
        }
        if (!state.episode.isHolding) return onPlayRequested(state)
        logger.interruption { "hold cleared by a play" }
        return onPlayRequested(state.copy(episode = state.episode.cleared()))
    }

    /** A pause from the user: what an audio interruption would have resumed is theirs to resume from now on. */
    private fun onUserPauseRequested(state: FastSessionState): FastSessionTransition {
        if (state.episode.isHolding && !state.episode.isResumeCancelled) logger.interruption { "hold cancelled by a user action" }
        return onPauseRequested(state.copy(episode = state.episode.cancelResume()))
    }

    private fun onAudioEnvironmentChanged(state: FastSessionState, input: AudioEnvironmentChanged): FastSessionTransition {
        val signal = input.signal
        // A headset went away: pause as the user would, and never resume on the device speaker by itself.
        if (signal == OutputDisconnected) {
            logger.interruption { "output disconnected, the session pauses" }
            return onPauseRequested(state.copy(episode = state.episode.cancelResume()))
        }
        // Read-aloud has no microphone.
        if (signal == MicSilenced || signal == MicUnsilenced) return FastSessionTransition(state, emptyList())
        val step = state.episode.onSignal(signal, input.at)
        val updated = state.copy(episode = step.episode)
        return when (val change = step.change) {
            is EpisodeChange.Started -> onEpisodeStarted(updated, change.tier)
            is EpisodeChange.Escalated -> onEpisodeEscalated(updated, change)
            is EpisodeChange.Ended -> onEpisodeEnded(updated, change.end)
            null -> FastSessionTransition(updated, emptyList())
        }
    }

    private fun onEpisodeStarted(state: FastSessionState, tier: InterruptionTier): FastSessionTransition {
        logger.interruption { "started as $tier" }
        return if (tier == InterruptionTier.Blip) {
            FastSessionTransition(state, listOf(StartBlipTimer(InterruptionEpisode.BLIP_SPEECH_THRESHOLD)))
        } else {
            holdSession(dropTemporaryPauseFromCallOn(state, tier))
        }
    }

    private fun onEpisodeEscalated(state: FastSessionState, change: EpisodeChange.Escalated): FastSessionTransition {
        logger.interruption { InterruptionPolicy.escalationLog(change) }
        val calm = dropTemporaryPauseFromCallOn(state, change.to)
        if (change.from != InterruptionTier.Blip) return FastSessionTransition(calm, emptyList())
        return holdSession(calm).prepend(listOf(CancelBlipTimer))
    }

    private fun dropTemporaryPauseFromCallOn(state: FastSessionState, tier: InterruptionTier): FastSessionState =
        if (InterruptionPolicy.dropsTemporaryPause(tier) && state.pauseReason == FastPauseReason.Temporary) {
            state.copy(pauseReason = FastPauseReason.User)
        } else {
            state
        }

    /**
     * Pauses as a user pause would, and leaves the resume to the end of the episode. A session that
     * something else already paused, or holds, has nothing to resume.
     */
    private fun holdSession(state: FastSessionState): FastSessionTransition {
        if (!state.isReading) {
            logger.interruption { "session already paused, nothing to resume" }
            return FastSessionTransition(state.copy(episode = state.episode.cancelResume()), emptyList())
        }
        // The player reports the pause later; an end of the episode before that must still find the session paused.
        return FastSessionTransition(
            state.copy(pauseReason = FastPauseReason.User, isPlaying = false),
            state.cancelPauseEffects() + PausePlayback,
        )
    }

    private fun onEpisodeEnded(state: FastSessionState, end: EpisodeEnd): FastSessionTransition {
        val cancelBlipTimer = FastSessionTransition(state, listOf(CancelBlipTimer))
        if (end == EpisodeEnd.AutoResume) return autoResume(state).prepend(cancelBlipTimer.effects)
        InterruptionPolicy.endLog(end)?.let { text -> logger.interruption { text } }
        return cancelBlipTimer
    }

    private fun autoResume(state: FastSessionState): FastSessionTransition {
        val skip = InterruptionPolicy.autoResumeSkip(
            isComplete = false,
            isPausedForAnotherReason = state.pauseReason != FastPauseReason.User,
            episode = state.episode,
        )
        if (skip != null) {
            logger.interruption { "auto-resume skipped, ${skip.text}" }
            return FastSessionTransition(state, emptyList())
        }
        logger.interruption { "auto-resume" }
        return resume(state)
    }

    /**
     * A blip that already lasted too long meets a part about to be read: it becomes an interruption
     * now, and the part is presented paused instead of read over the other app.
     */
    private fun escalateLongBlip(transition: FastSessionTransition): FastSessionTransition {
        val state = transition.state
        val isAboutToRead = state.readAloudStep == Question || state.readAloudStep == Answer
        val isReading = state.isReading || transition.effects.any { it == Play }
        if (!state.episode.isLongBlip || !isAboutToRead || !isReading) return transition
        val step = state.episode.escalateBlip()
        val change = step.change as? EpisodeChange.Escalated ?: return transition
        logger.interruption { InterruptionPolicy.escalationLog(change) }
        return FastSessionTransition(
            state.copy(episode = step.episode, pauseReason = FastPauseReason.User, isPlaying = false),
            listOf(CancelBlipTimer, PausePlayback) + transition.effects.filterNot { it == Play },
        )
    }

    /** A transition that makes the session play again withdraws the resume an interruption was waiting to make. */
    private fun withdrawResumeOnPlay(transition: FastSessionTransition): FastSessionTransition {
        val isPlay = transition.effects.any { it.isPlay }
        if (!isPlay || !transition.state.episode.isHolding) return transition
        return transition.copy(state = transition.state.copy(episode = transition.state.episode.cancelResume()))
    }

    /** The one choke point of the call block: no play starts anything while a call rings or runs, whatever asked for it. */
    private fun stripPlayDuringCall(transition: FastSessionTransition): FastSessionTransition =
        if (transition.state.episode.isCallBlocking) transition.copy(effects = transition.effects.filterNot { it.isPlay }) else transition

    private val FastSessionEffect.isPlay: Boolean get() = this == Play || this is FastSessionEffect.RestartVoiceStack

    private fun FastSessionState.isHeldByResumableInterruption(): Boolean =
        episode.tier == InterruptionTier.Interruption && !episode.isResumeCancelled && pauseReason == FastPauseReason.User

    private fun FastSessionTransition.prepend(before: List<FastSessionEffect>): FastSessionTransition = copy(effects = before + effects)
}

/** The player plays and nothing paused or held the session. */
internal val FastSessionState.isReading: Boolean
    get() = isPlaying && pauseReason == null && !isHeldAtAdvancePoint && !isPausedAtAdvancePoint
