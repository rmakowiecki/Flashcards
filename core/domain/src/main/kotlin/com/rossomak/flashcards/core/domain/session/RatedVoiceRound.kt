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
        // The question was read again while the microphone was open: restart the window.
        Listening, SpeechDetected -> if (state.round.cardId == cardId) {
            emit(CancelSilenceTimer)
            openListeningWindow(cardId)
        }
        VoiceAnswerPhase.Idle, Grading, SpeakingNotice -> Unit
    }
}

private fun RatedTransitionBuilder.openListeningWindow(cardId: String) {
    state = state.copy(round = VoiceAnswerRound(phase = Listening, cardId = cardId))
    emit(RatedSessionEffect.OpenListeningWindow(cardId))
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
 * its feedback is still spoken.
 */
internal fun RatedTransitionBuilder.onGraded(grade: VoiceAnswerGrade) {
    if (state.round.phase != Grading || !state.round.hasUtterance) return
    val rating = grade.toFlashcardAttemptRating()
    if (state.round.cardId == state.currentCard?.id) {
        state = recordRating(state, rating, random).copy(consecutiveSilenceCount = 0, consecutiveGradingFailureCount = 0)
    }
    state = state.copy(round = state.round.copy(phase = SpeakingNotice, grade = grade))
    speakNotice(SpokenNotice.Feedback(rating, grade.feedback))
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
 */
internal fun RatedTransitionBuilder.onGradingFailed(reason: GradingFailureReason) {
    if (state.round.phase != Grading || !state.round.hasUtterance) return
    state = recordSilence(state, random).copy(
        consecutiveGradingFailureCount = state.consecutiveGradingFailureCount + 1,
        round = state.round.copy(phase = SpeakingNotice, gradingFailure = reason),
    )
    if (state.consecutiveGradingFailureCount >= CONSECUTIVE_GRADING_FAILURE_PAUSE_THRESHOLD) {
        speakNotice(SpokenNotice.GradingPause)
        pauseVoiceAnswering(VoiceAnswerPauseReason.GradingFailures)
        emit(Emit(VoiceAnswerGradingPause))
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

/** The notice being spoken keeps speaking; everything else of the round stops. */
private fun RatedTransitionBuilder.pauseVoiceAnswering(reason: VoiceAnswerPauseReason) {
    if (state.isPlaying) emit(RatedSessionEffect.PausePlayback)
    emit(RatedSessionEffect.StopVoiceAnswering)
    emit(CancelGrading)
    state = state.copy(voiceAnswerPauseReason = reason, round = VoiceAnswerRound(), isPausedAtAdvancePoint = false)
}

private fun RatedTransitionBuilder.speakNotice(notice: SpokenNotice) {
    state = state.copy(speakingNotices = state.speakingNotices + notice)
    emit(RatedSessionEffect.SpeakNotice(notice))
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
 * The tail after an advancing notice: the queue syncs first, then the next question is read, or the
 * session is held at the advance point when playback was paused meanwhile.
 */
internal fun RatedTransitionBuilder.onNoticeTailElapsed() {
    if (state.isSyncPending) syncQueue()
    if (!state.isVoiceAnsweringActive || state.round.phase != SpeakingNotice) {
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
