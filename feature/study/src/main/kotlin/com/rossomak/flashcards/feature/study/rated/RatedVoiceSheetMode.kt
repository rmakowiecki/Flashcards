package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Legacy
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Listening
import com.rossomak.flashcards.feature.study.rated.RatedVoiceSheetMode.Transport
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase

/**
 * What the bottom sheet of a voice-answering Rated Study Session shows. Exactly one mode at a
 * time, derived from the voice-answering phase and pause state by [voiceSheetModeOf].
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
     * Grading, the spoken notice and grading failures, still rendered as transcript, grade text and
     * transport row until they get dedicated modes.
     */
    data object Legacy : RatedVoiceSheetMode
}

/** A pause, or voice answering not (yet) running, always wins over the phase. */
fun voiceSheetModeOf(isVoiceAnswerEnabled: Boolean, voiceAnswerPhase: VoiceAnswerPhase, isVoiceAnswerPaused: Boolean): RatedVoiceSheetMode = when {
    isVoiceAnswerPaused || !isVoiceAnswerEnabled -> Transport
    else -> when (voiceAnswerPhase) {
        VoiceAnswerPhase.Idle, VoiceAnswerPhase.WaitingForQuestion -> Transport
        VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected -> Listening
        VoiceAnswerPhase.Grading, VoiceAnswerPhase.SpeakingNotice -> Legacy
    }
}
