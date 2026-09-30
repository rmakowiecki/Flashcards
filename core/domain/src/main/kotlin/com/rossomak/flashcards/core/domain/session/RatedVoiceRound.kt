@file:Suppress("TooManyFunctions") // one transition per voice-answering input of the reducer.

package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase.Grading
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase.Listening
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase.SpeakingNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase.SpeechDetected
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase.WaitingForQuestion
import com.rossomak.flashcards.core.domain.model.VoiceAnswerRound
import com.rossomak.flashcards.core.domain.model.toFlashcardAttemptRating
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelGrading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopListening
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerGradingFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerGradingPause
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerSilencePause
import com.rossomak.flashcards.core.domain.session.RatedSessionEvent.VoiceAnswerSilenceSkip

// The Voice Answering round transitions of RatedSessionReducer, reached only through its reduce().

internal fun RatedTransitionBuilder.onQuestionFinished(cardId: String) {
    val isForHead = cardId == state.currentCard?.id
    if (!state.isVoiceAnsweringActive || !isForHead || state.speakingNotices.isNotEmpty()) return
    when (state.round.phase) {
        WaitingForQuestion -> openListeningWindow(cardId)
        // The question was read again while the window was open: close it, so the microphone is
        // never open while a question is read, and open a fresh one.
        Listening, SpeechDetected -> if (state.round.cardId == cardId) {
            emit(StopListening)
            openListeningWindow(cardId)
        }
        VoiceAnswerPhase.Idle, Grading, SpeakingNotice -> Unit
    }
}

private fun RatedTransitionBuilder.openListeningWindow(cardId: String) {
    state = state.copy(round = VoiceAnswerRound(phase = Listening, cardId = cardId))
    emit(RatedSessionEffect.OpenListeningWindow(cardId))
}

/**
 * The window's microphone records: the silence timer starts and the listening cue plays, once per
 * window. A repeat, such as after the capture switched devices, or a late report after the window
 * closed changes nothing.
 */
internal fun RatedTransitionBuilder.onMicrophoneOpened() {
    if (state.round.phase != Listening || state.round.isMicrophoneOpen) return
    state = state.copy(round = state.round.copy(isMicrophoneOpen = true))
    emit(RatedSessionEffect.StartSilenceTimer)
    emit(RatedSessionEffect.PlayListeningCue)
}

/**
 * The player stopped on its own, such as for another app's sound. An open window closes as for a
 * user pause, so the microphone is never open while the question is read again, and the next
 * finished question opens a fresh one.
 */
internal fun RatedTransitionBuilder.onPlayerStopped() {
    if (state.round.phase != Listening && state.round.phase != SpeechDetected) return
    emit(StopListening)
    state = state.copy(round = state.idleRound())
}

internal fun RatedTransitionBuilder.onSpeechStarted() {
    if (state.round.phase != Listening) return
    state = state.copy(round = state.round.copy(phase = SpeechDetected))
    emit(CancelSilenceTimer)
}

internal fun RatedTransitionBuilder.onSpeechEnded() {
    if (state.round.phase != Listening && state.round.phase != SpeechDetected) return
    enterGrading()
}

internal fun RatedTransitionBuilder.onUtteranceCaptured(obfuscatedWav: ByteArray) {
    val round = state.round
    val isOpen = round.phase == Listening || round.phase == SpeechDetected || round.phase == Grading
    val card = state.queue.firstOrNull { it.card.id == round.cardId }?.card
    if (!isOpen || round.hasUtterance || card == null) return
    emit(CancelSilenceTimer)
    emit(StopListening)
    enterGrading()
    state = state.copy(round = state.round.copy(hasUtterance = true))
    emit(RatedSessionEffect.Grade(card, obfuscatedWav))
}

/** Grading reveals the answer at once, so the user can check what they missed while it runs. */
private fun RatedTransitionBuilder.enterGrading() {
    state = state.copy(round = state.round.copy(phase = Grading), isAnswerRevealed = true)
}

internal fun RatedTransitionBuilder.onTranscriptReady(transcript: String) {
    if (state.round.phase != Grading) return
    state = state.copy(round = state.round.copy(transcript = transcript))
}

/**
 * A grade rates the head exactly like a manual tap, but the head stays until the feedback notice
 * has finished. A grade for a card that is no longer the head is stale: its Rating is dropped, but
 * its feedback is still spoken. A grade that arrives while paused is recorded all the same, and
 * its feedback waits for the next play.
 */
internal fun RatedTransitionBuilder.onGraded(grade: VoiceAnswerGrade) {
    if (state.round.phase != Grading || !state.round.hasUtterance) return
    val rating = grade.toFlashcardAttemptRating()
    if (state.round.cardId == state.currentCard?.id) {
        state = recordRating(state, rating, random).copy(consecutiveSilenceCount = 0, consecutiveGradingFailureCount = 0)
    }
    state = state.copy(round = state.round.copy(phase = SpeakingNotice, grade = grade))
    if (state.isPausedWhileGrading) {
        state = state.copy(isPausedWhileGrading = false, isPausedAfterFeedback = true)
    } else {
        speakNotice(SpokenNotice.Feedback(rating, grade.feedback))
    }
}

/** Nothing heard: the card goes back unanswered, and the third silence in a row pauses voice answering. */
internal fun RatedTransitionBuilder.onSilenceTimedOut() {
    if (state.round.phase != Listening) return
    emit(StopListening)
    state = recordSilence(state, random).copy(
        consecutiveSilenceCount = state.consecutiveSilenceCount + 1,
        round = state.round.copy(phase = SpeakingNotice),
    )
    if (state.consecutiveSilenceCount >= CONSECUTIVE_SILENCE_PAUSE_THRESHOLD) {
        speakNotice(SpokenNotice.SilencePause)
        pauseVoiceAnswering(VoiceAnswerPauseReason.Silence)
        emit(Emit(VoiceAnswerSilencePause))
    } else {
        speakNotice(SpokenNotice.SilenceSkip)
        emit(Emit(VoiceAnswerSilenceSkip))
    }
}

/**
 * The answer could not be graded: the card goes back unanswered, like after a silence, counted on
 * its own counter. The third failure in a row pauses voice answering.
 *
 * A failure that arrives while paused still reports itself and applies its effect, but its notice
 * is not spoken: the session waits paused at the auto-advance point, or voice answering pauses.
 */
internal fun RatedTransitionBuilder.onGradingFailed(reason: GradingFailureReason) {
    if (state.round.phase != Grading || !state.round.hasUtterance) return
    val isPaused = state.isPausedWhileGrading
    state = recordSilence(state, random).copy(
        consecutiveGradingFailureCount = state.consecutiveGradingFailureCount + 1,
        round = state.round.copy(phase = SpeakingNotice, gradingFailure = reason),
        isPausedWhileGrading = false,
    )
    if (state.consecutiveGradingFailureCount >= CONSECUTIVE_GRADING_FAILURE_PAUSE_THRESHOLD) {
        if (!isPaused) speakNotice(SpokenNotice.GradingPause)
        pauseVoiceAnswering(VoiceAnswerPauseReason.GradingFailures)
        emit(Emit(VoiceAnswerGradingPause))
        // Without a notice to wait for, the sync runs now.
        if (isPaused) {
            syncQueue()
            if (state.isComplete) emit(SessionComplete)
        }
    } else if (isPaused) {
        emit(Emit(VoiceAnswerGradingFailed(reason)))
        syncQueue()
        state = state.copy(round = state.idleRound())
        if (state.isComplete) emit(SessionComplete) else state = state.copy(isPausedAtAdvancePoint = true)
    } else {
        speakNotice(SpokenNotice.GradingFailed(reason))
        emit(Emit(VoiceAnswerGradingFailed(reason)))
    }
}

/**
 * The microphone failed: voice answering pauses on the presented card, which starts over from its
 * question on resume. No requeue and no counter: the user did nothing wrong.
 */
internal fun RatedTransitionBuilder.onCaptureFailed() {
    if (!state.isVoiceAnsweringActive) return
    emit(StopListening)
    emit(CancelSilenceTimer)
    emit(CancelGrading)
    speakNotice(SpokenNotice.CaptureFailed)
    pauseVoiceAnswering(VoiceAnswerPauseReason.CaptureFailed)
    emit(RatedSessionEffect.RestartCurrentCard)
    emit(Emit(VoiceAnswerCaptureUnavailable))
}

/**
 * The notice being spoken keeps speaking; everything else of the round stops. The player is paused
 * even when it already is, which drops an auto-resume it has pending from a lost audio focus: only
 * a play through the session may resume, and it resumes voice answering.
 */
private fun RatedTransitionBuilder.pauseVoiceAnswering(reason: VoiceAnswerPauseReason) {
    emit(RatedSessionEffect.PausePlayback)
    emit(RatedSessionEffect.StopVoiceAnswering)
    emit(CancelGrading)
    state = state.copy(
        voiceAnswerPauseReason = reason,
        round = VoiceAnswerRound(),
        isPausedAtAdvancePoint = false,
        isHeldAtAdvancePoint = false,
        isPausedWhileGrading = false,
        isPausedAfterFeedback = false,
    )
}

private fun RatedTransitionBuilder.speakNotice(notice: SpokenNotice) {
    state = state.copy(speakingNotices = state.speakingNotices + notice)
    emit(RatedSessionEffect.SpeakNotice(notice))
}

/**
 * A user pause, by phase. The open microphone closes and the round starts over from the question,
 * with nothing counted. Grading goes on. The grading feedback stops before the auto-advance point,
 * so the queue sync waits, and it is read again from the start on play. A short notice always
 * finishes, and the tail after it finds the session paused.
 */
internal fun RatedTransitionBuilder.pauseVoiceRound() {
    if (!state.isVoiceAnsweringActive) return
    when {
        state.isFeedbackPlaying -> {
            stopFeedback()
            emit(RatedSessionEffect.CancelNoticeTail)
            state = state.copy(isPausedAfterFeedback = true)
        }
        state.round.phase == Listening || state.round.phase == SpeechDetected -> {
            emit(StopListening)
            state = state.copy(round = state.idleRound())
        }
        state.round.phase == Grading -> state = state.copy(isPausedWhileGrading = true)
        else -> Unit
    }
}

/** Stops the feedback and reaches the auto-advance point at once, the same way its natural end does. */
internal fun RatedTransitionBuilder.skipFeedback() {
    stopFeedback()
    emit(RatedSessionEffect.CancelNoticeTail)
    moveOnFromAdvancePoint()
}

/** Reads the whole feedback again. The Rating was applied when the grade arrived and is never applied twice. */
internal fun RatedTransitionBuilder.replayFeedback() {
    val grade = state.round.grade ?: return
    state = state.copy(isPausedAfterFeedback = false)
    emit(RatedSessionEffect.ResumeWithoutReading)
    speakNotice(SpokenNotice.Feedback(grade.toFlashcardAttemptRating(), grade.feedback))
}

/** The feedback leaves [RatedSessionState.speakingNotices] here, since a stopped feedback never reports finished. */
private fun RatedTransitionBuilder.stopFeedback() {
    if (state.speakingNotices.none { it is SpokenNotice.Feedback }) return
    state = state.copy(speakingNotices = state.speakingNotices.filterNot { it is SpokenNotice.Feedback })
    emit(RatedSessionEffect.StopFeedback)
}

/**
 * An advancing notice finished with voice answering still on: wait the tail, then move on. Any
 * other notice, or one finishing after voice answering paused, only lets the waiting queue sync run.
 */
internal fun RatedTransitionBuilder.onNoticeFinished(notice: SpokenNotice) {
    val index = state.speakingNotices.indexOf(notice)
    if (index < 0) return
    state = state.copy(speakingNotices = state.speakingNotices.filterIndexed { noticeIndex, _ -> noticeIndex != index })
    if (notice.isAdvancing && state.isVoiceAnsweringActive && state.round.phase == SpeakingNotice) {
        emit(RatedSessionEffect.StartNoticeTail)
    } else if (state.speakingNotices.isEmpty() && state.isSyncPending) {
        syncQueue()
        if (state.isComplete) emit(SessionComplete)
    }
}

/**
 * The tail after an advancing notice ends at the auto-advance point. With a hold requested the
 * session stops there, on the answered card, with its queue sync still pending. Otherwise the queue
 * syncs first, then the next question is read, or the session waits at the advance point when
 * playback was paused meanwhile.
 */
internal fun RatedTransitionBuilder.onNoticeTailElapsed() {
    val isAdvancePoint = state.isVoiceAnsweringActive && state.round.phase == SpeakingNotice
    if (isAdvancePoint && state.isPlaying && state.isAdvanceHoldRequested) {
        state = state.copy(isHeldAtAdvancePoint = true)
        emit(RatedSessionEffect.PausePlayback)
        return
    }
    if (state.isSyncPending) syncQueue()
    if (!isAdvancePoint) {
        if (state.isComplete) emit(SessionComplete)
        return
    }
    state = state.copy(round = state.idleRound())
    when {
        state.isComplete -> emit(SessionComplete)
        state.isPlaying -> emit(RatedSessionEffect.AdvanceAfterVoiceAnswer)
        else -> state = state.copy(isPausedAtAdvancePoint = true)
    }
}

private val SpokenNotice.isAdvancing: Boolean
    get() = when (this) {
        is SpokenNotice.Feedback, SpokenNotice.SilenceSkip, is SpokenNotice.GradingFailed -> true
        SpokenNotice.SilencePause, SpokenNotice.GradingPause, SpokenNotice.CaptureFailed -> false
    }
