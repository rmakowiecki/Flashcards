package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.toFlashcardAttemptRating
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Graded
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.GradingWithTranscript
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Listening
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Pending
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Transport
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase

/**
 * What the bottom sheet of a voice-answering Rated Study Session shows. Exactly one mode at a
 * time, derived from the voice-answering phase, transcript, grade, short-notice and pause state by
 * [voiceSheetModeOf].
 */
sealed interface RatedVoiceSheetMode {

    /**
     * The question is being read, no voice round is running, or the session is paused: the
     * transport row plus the voice-settings cog. The only mode with the cog, since changing voice
     * settings mid-round would create states the session does not model.
     */
    data object Transport : RatedVoiceSheetMode

    /** The microphone is open: only the live capture indicator, centered. */
    data object Listening : RatedVoiceSheetMode

    /**
     * Waiting with nothing to show yet, or a short notice (silence skip or pause, grading or capture
     * failure) is being spoken: only the progress disc, centered, with no controls.
     */
    data object Pending : RatedVoiceSheetMode

    /** The user's sanitized [transcript] is known and is being graded. */
    data class GradingWithTranscript(val transcript: String) : RatedVoiceSheetMode

    /** The grade is being read aloud: the [rating] it maps to and the grader's [rationale]. */
    data class Graded(val rating: FlashcardAttemptRating, val rationale: String) : RatedVoiceSheetMode
}

/**
 * A short notice still being spoken wins over everything, including a pause that landed before it
 * finished; otherwise a pause, or voice answering not (yet) running, wins over the phase.
 */
fun voiceSheetModeOf(
    isVoiceAnswerEnabled: Boolean,
    voiceAnswerPhase: VoiceAnswerPhase,
    isVoiceAnswerPaused: Boolean,
    isShortNoticeSpeaking: Boolean,
    sanitizedTranscript: String?,
    lastGrade: VoiceAnswerGrade?,
): RatedVoiceSheetMode = when {
    isShortNoticeSpeaking -> Pending
    isVoiceAnswerPaused || !isVoiceAnswerEnabled -> Transport
    else -> when (voiceAnswerPhase) {
        VoiceAnswerPhase.Idle, VoiceAnswerPhase.WaitingForQuestion -> Transport
        VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected -> Listening
        VoiceAnswerPhase.Grading -> if (sanitizedTranscript.isNullOrBlank()) Pending else GradingWithTranscript(sanitizedTranscript)
        VoiceAnswerPhase.SpeakingNotice -> if (lastGrade == null) Pending else Graded(lastGrade.toFlashcardAttemptRating(), lastGrade.feedback)
    }
}
