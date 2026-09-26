package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.toFlashcardAttemptRating
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Graded
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.GradingWithTranscript
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Legacy
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Listening
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Pending
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Transport
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase

/**
 * What the bottom sheet of a voice-answering Rated Study Session shows. Exactly one mode at a
 * time, derived from the voice-answering phase, transcript, grade and pause state by
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

    /** Waiting with nothing to show yet: only the progress disc, centered. */
    data object Pending : RatedVoiceSheetMode

    /** The user's sanitized [transcript] is known and is being graded. */
    data class GradingWithTranscript(val transcript: String) : RatedVoiceSheetMode

    /** The grade is being read aloud: the [rating] it maps to and the grader's [rationale]. */
    data class Graded(val rating: FlashcardAttemptRating, val rationale: String) : RatedVoiceSheetMode

    /**
     * A notice without a grade (grading failure or silence timeout), still rendered as transcript
     * and transport row until it gets a dedicated mode.
     */
    data object Legacy : RatedVoiceSheetMode
}

/** A pause, or voice answering not (yet) running, always wins over the phase. */
fun voiceSheetModeOf(
    isVoiceAnswerEnabled: Boolean,
    voiceAnswerPhase: VoiceAnswerPhase,
    isVoiceAnswerPaused: Boolean,
    sanitizedTranscript: String?,
    lastGrade: VoiceAnswerGrade?,
): RatedVoiceSheetMode = when {
    isVoiceAnswerPaused || !isVoiceAnswerEnabled -> Transport
    else -> when (voiceAnswerPhase) {
        VoiceAnswerPhase.Idle, VoiceAnswerPhase.WaitingForQuestion -> Transport
        VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected -> Listening
        VoiceAnswerPhase.Grading -> if (sanitizedTranscript.isNullOrBlank()) Pending else GradingWithTranscript(sanitizedTranscript)
        VoiceAnswerPhase.SpeakingNotice -> if (lastGrade == null) Legacy else Graded(lastGrade.toFlashcardAttemptRating(), lastGrade.feedback)
    }
}
