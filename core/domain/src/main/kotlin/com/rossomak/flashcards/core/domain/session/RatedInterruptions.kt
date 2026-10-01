@file:Suppress("TooManyFunctions") // one transition per audio-interruption reaction of the reducer.

package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.EpisodeChange
import com.rossomak.flashcards.core.domain.model.EpisodeEnd
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.InterruptionTier
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelBlipTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelGateTail
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PlayListeningCue
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.ResumeWithoutReading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SetCaptureGate
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartBlipTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartGateTail
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AudioEnvironmentChanged

// What other apps' audio does to a Rated voice round, as transitions of RatedSessionReducer. An
// interruption pauses through the same pause flags a user pause uses (pauseVoiceRound), so its
// per-phase behavior is the one a user pause already has; the InterruptionEpisode only remembers
// whether the end of the interruption may resume the session. Reached only through reduce().

internal fun RatedTransitionBuilder.onAudioEnvironmentChanged(input: AudioEnvironmentChanged) {
    when (input.signal) {
        OutputDisconnected -> onOutputDisconnected()
        // A microphone another app took only matters to a window that listens, or to an interruption
        // that already holds the session and must not end while the microphone is still gone.
        MicSilenced -> if (isListeningRound() || state.episode.isHolding) applySignal(input) else Unit
        else -> applySignal(input)
    }
}

private fun RatedTransitionBuilder.applySignal(input: AudioEnvironmentChanged) {
    val wasCallBlocking = state.episode.isCallBlocking
    val step = state.episode.onSignal(input.signal, input.at)
    state = state.copy(episode = step.episode)
    // The controls go dark with the call, so the user is told once, as it starts.
    if (!wasCallBlocking && state.episode.isCallBlocking) emit(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
    when (val change = step.change) {
        is EpisodeChange.Started -> onEpisodeStarted(change.tier)
        is EpisodeChange.Escalated -> onEpisodeEscalated(change)
        is EpisodeChange.Ended -> onEpisodeEnded(change.end)
        null -> Unit
    }
}

/**
 * The player starts playing while a call rings or runs, which no interruption holds: the session
 * opened while the call rang, so it never held focus to lose, and the reducer knows the call only by
 * the audio mode. The session pauses as for a user pause, and only the user's play resumes it. Only
 * the player's report triggers this, not the mode alone: during a session the mode comes just
 * before the focus loss that holds it, and pausing on the mode would end the ring's auto-resume.
 * A part that starts inside that gap does pause the session as a user pause, so a declined ring
 * then leaves it paused until the user plays: the narrow price of never playing over a ring.
 */
internal fun RatedTransitionBuilder.pausePlayingUnderCall() {
    if (!state.episode.isCallBlocking || !state.isPlaying || state.episode.isHolding) return
    logger.interruption { "the player plays under a call, the session pauses" }
    pauseByUser()
    // The player reports the pause later; a play request before that must still find the session paused.
    state = state.copy(isPlaying = false)
}

private fun RatedTransitionBuilder.onEpisodeStarted(tier: InterruptionTier) {
    logger.interruption { "started as $tier" }
    if (tier == InterruptionTier.Blip) {
        startBlip()
    } else {
        dropTemporaryPauseFromCallOn(tier)
        holdSession()
    }
}

private fun RatedTransitionBuilder.onEpisodeEscalated(change: EpisodeChange.Escalated) {
    logger.interruption { InterruptionPolicy.escalationLog(change) }
    dropTemporaryPauseFromCallOn(change.to)
    if (change.from == InterruptionTier.Blip) {
        emit(CancelBlipTimer)
        holdSession()
    }
}

/** A dialog closing must not play over a call or another media app that took over: from a call on, only the user's play resumes. */
private fun RatedTransitionBuilder.dropTemporaryPauseFromCallOn(tier: InterruptionTier) {
    if (InterruptionPolicy.dropsTemporaryPause(tier) && state.isPausedTemporarily) state = state.copy(isPausedTemporarily = false)
}

private fun RatedTransitionBuilder.startBlip() {
    emit(StartBlipTimer(InterruptionEpisode.BLIP_SPEECH_THRESHOLD))
    if (state.isCaptureGated) {
        emit(CancelGateTail)
        return
    }
    state = state.copy(isCaptureGated = true)
    emit(SetCaptureGate(closed = true))
    if (isListeningWindowOpen()) emit(CancelSilenceTimer)
    logger.interruption { "capture gate closed" }
}

/**
 * The timer of a blip ran out. While something speaks, the blip is now an interruption. A listening
 * window stays gated until a much longer limit, when its round is cancelled instead. While nothing
 * speaks, the blip is only marked long, so the next thing to speak treats it as an interruption.
 */
internal fun RatedTransitionBuilder.onBlipElapsed() {
    val episode = state.episode
    if (episode.tier != InterruptionTier.Blip) return
    when {
        isListeningRound() && episode.isBlipLong -> escalateBlipAndHold(cancelsResume = true)
        isListeningRound() -> {
            state = state.copy(episode = episode.markBlipLong())
            emit(StartBlipTimer(InterruptionEpisode.LISTENING_BLIP_ESCALATION - InterruptionEpisode.BLIP_SPEECH_THRESHOLD))
        }
        isSpeechPhase() -> escalateBlipAndHold(cancelsResume = false)
        else -> state = state.copy(episode = episode.markBlipLong())
    }
}

private fun RatedTransitionBuilder.escalateBlipAndHold(cancelsResume: Boolean) {
    val step = state.episode.escalateBlip()
    val change = step.change as? EpisodeChange.Escalated ?: return
    state = state.copy(episode = if (cancelsResume) step.episode.cancelResume() else step.episode)
    logger.interruption { InterruptionPolicy.escalationLog(change) }
    holdSession()
}

/**
 * A blip that already lasted too long meets something that is about to speak: it becomes an
 * interruption now. Returns whether it did, so the caller holds instead of speaking.
 */
internal fun RatedTransitionBuilder.escalateLongBlip(): Boolean {
    if (!state.episode.isLongBlip) return false
    val step = state.episode.escalateBlip()
    val change = step.change as? EpisodeChange.Escalated ?: return false
    state = state.copy(episode = step.episode)
    emit(CancelBlipTimer)
    logger.interruption { InterruptionPolicy.escalationLog(change) }
    return true
}

/**
 * Pauses like a user pause would, by phase, and leaves the resume to the end of the episode. A
 * session that something else already paused, or holds, has nothing to resume: the end of the
 * episode changes nothing, and whatever paused it resumes it.
 */
private fun RatedTransitionBuilder.holdSession() {
    if (state.isPausedForAnotherReason()) {
        state = state.copy(episode = state.episode.cancelResume())
        logger.interruption { "session already paused, nothing to resume" }
        return
    }
    pauseVoiceRound()
    // The player reports the pause later; an end of the episode before that must still find the session paused.
    state = state.copy(isPlaying = false)
    emit(PausePlayback)
}

private fun RatedTransitionBuilder.onEpisodeEnded(end: EpisodeEnd) {
    emit(CancelBlipTimer)
    if (state.isCaptureGated) emit(StartGateTail)
    if (end == EpisodeEnd.AutoResume) autoResume() else InterruptionPolicy.endLog(end)?.let { text -> logger.interruption { text } }
}

private fun RatedTransitionBuilder.autoResume() {
    val skip = InterruptionPolicy.autoResumeSkip(
        isComplete = state.isComplete,
        isPausedForAnotherReason = state.pauseReason != null || state.voiceAnswerPauseReason != null,
        episode = state.episode,
    )
    if (skip != null) {
        logger.interruption { "auto-resume skipped, ${skip.text}" }
        return
    }
    logger.interruption { "auto-resume" }
    if (state.isNoticeInFlight()) {
        // A notice, or the tail after it, still runs; the round goes on with it and reads the next question afterwards.
        state = state.copy(isPlaying = true)
        emit(ResumeWithoutReading)
    } else {
        onPlayRequested()
    }
}

/**
 * The gate tail ran in full. A blip that started meanwhile keeps the gate closed; its own end
 * starts a tail again.
 */
internal fun RatedTransitionBuilder.onGateTailElapsed() {
    if (!state.isCaptureGated || state.episode.tier == InterruptionTier.Blip) return
    reopenGate()
}

/** Opens the gate at once, without a tail, for an episode that ends without a gain. */
private fun RatedTransitionBuilder.openGateNow() {
    if (!state.isCaptureGated) return
    emit(CancelGateTail)
    reopenGate()
}

private fun RatedTransitionBuilder.reopenGate() {
    state = state.copy(isCaptureGated = false)
    emit(SetCaptureGate(closed = false))
    logger.interruption { "capture gate opened" }
    if (isListeningWindowOpen()) {
        emit(StartSilenceTimer)
        if (state.isListeningCueHeld) emit(PlayListeningCue)
    }
    state = state.copy(isListeningCueHeld = false)
}

/** A user pause: whatever the interruption would have resumed is now the user's to resume. */
internal fun RatedTransitionBuilder.cancelResumeOfEpisode() {
    if (!state.episode.isHolding || state.episode.isResumeCancelled) return
    state = state.copy(episode = state.episode.cancelResume())
    logger.interruption { "hold cancelled by a user action" }
}

/**
 * A headset went away. The session pauses as for a user pause and never resumes on the device
 * speaker by itself: not at the end of an interruption, and not when the headset comes back.
 */
private fun RatedTransitionBuilder.onOutputDisconnected() {
    logger.interruption { "output disconnected, the session pauses" }
    if (state.episode.isActive) state = state.copy(episode = state.episode.cancelResume())
    pauseByUser()
}

/**
 * A play the user asked for. Nothing may start while a call rings or runs. Any other interruption
 * gives way to the user: the hold and its pending resume are dropped and the session plays now.
 */
internal fun RatedTransitionBuilder.onUserPlayRequested() {
    if (InterruptionPolicy.ignoresPlay(state.episode, state.isAwaitingPlay())) {
        emit(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
        return
    }
    if (state.episode.isHolding) {
        state = state.copy(episode = state.episode.cleared())
        logger.interruption { "hold cleared by a play" }
        openGateNow()
    }
    onPlayRequested()
}

/** The end of a temporary pause never plays over a call; the pause ends, and the next play resumes. */
internal fun RatedTransitionBuilder.onTemporaryPauseEnded() {
    if (!state.isPausedTemporarily) return
    if (state.episode.isCallBlocking) {
        state = state.copy(isPausedTemporarily = false)
    } else {
        onPlayRequested()
    }
}

/**
 * A resume the user asked for after an engine failure or a voice-answer pause. Blocked while a call
 * rings or runs, like a play.
 */
internal fun RatedTransitionBuilder.isResumeBlockedByCall(): Boolean {
    val hasResume = state.pauseReason != null || state.voiceAnswerPauseReason != null
    if (!hasResume || !state.episode.isCallBlocking) return false
    emit(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
    return true
}

private fun RatedSessionState.isPausedForAnotherReason(): Boolean =
    isComplete || !isPlaying || pauseReason != null || voiceAnswerPauseReason != null || isAwaitingPlay()

/** Whether a play would have anything to start. */
internal fun RatedSessionState.isAwaitingPlay(): Boolean =
    !isPlaying || isPausedTemporarily || isPausedAtAdvancePoint || isPausedAfterFeedback || isPausedWhileGrading || isHeldAtAdvancePoint

/** A notice, or the tail after one, is still running: the round goes on by itself. */
private fun RatedSessionState.isNoticeInFlight(): Boolean =
    round.phase == VoiceAnswerPhase.SpeakingNotice && !isPausedAfterFeedback && !isPausedAtAdvancePoint && !isHeldAtAdvancePoint

private fun RatedTransitionBuilder.isListeningRound(): Boolean =
    state.isVoiceAnsweringActive && (state.round.phase == VoiceAnswerPhase.Listening || state.round.phase == VoiceAnswerPhase.SpeechDetected)

private fun RatedTransitionBuilder.isListeningWindowOpen(): Boolean =
    state.round.phase == VoiceAnswerPhase.Listening && state.round.isMicrophoneOpen

/** The question or the grading feedback is being read: the phases a blip can talk over. */
private fun RatedTransitionBuilder.isSpeechPhase(): Boolean = with(state) {
    isFeedbackPlaying ||
        (
            isVoiceAnsweringActive &&
                isPlaying &&
                round.phase == VoiceAnswerPhase.WaitingForQuestion &&
                speakingNotices.isEmpty() &&
                !isAwaitingPlay()
            )
}
