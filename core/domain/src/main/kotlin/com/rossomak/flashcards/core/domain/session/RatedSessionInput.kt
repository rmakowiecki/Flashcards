package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState

/** Everything that can happen to a Rated Study Session, as [RatedSessionReducer] takes it in. */
sealed interface RatedSessionInput {

    /** A manual self-rating of the presented card. */
    data class AttemptRated(val rating: FlashcardAttemptRating) : RatedSessionInput

    /** The user asked to see the answer. */
    data object AnswerRevealed : RatedSessionInput

    /** "Next", from the app or from outside it: skip the presented card. */
    data object CardSkipped : RatedSessionInput

    /** A tap on the grading feedback: skip it and move on. Acts only while the feedback plays. */
    data object FeedbackSkipRequested : RatedSessionInput

    /** "Previous", from the app or from outside it: restart the presented card's question. */
    data object PreviousRequested : RatedSessionInput
    data class QuestionFinished(val cardId: String) : RatedSessionInput

    /** The microphone is recording for the open listening window. May repeat within one window. */
    data object MicrophoneOpened : RatedSessionInput
    data object SpeechStarted : RatedSessionInput
    data object SpeechEnded : RatedSessionInput

    /** The captured answer, already obfuscated. Handed straight to grading and never kept. */
    class UtteranceCaptured(val obfuscatedWav: ByteArray) : RatedSessionInput
    data object SilenceTimedOut : RatedSessionInput
    data class TranscriptReady(val transcript: String) : RatedSessionInput
    data class Graded(val grade: VoiceAnswerGrade) : RatedSessionInput
    data class GradingFailed(val reason: GradingFailureReason) : RatedSessionInput
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : RatedSessionInput
    data class NoticeFinished(val notice: SpokenNotice) : RatedSessionInput
    data object NoticeTailElapsed : RatedSessionInput
    data object PlayRequested : RatedSessionInput
    data object PauseRequested : RatedSessionInput
    data object TemporaryPauseRequested : RatedSessionInput
    data object TemporaryPauseEnded : RatedSessionInput
    data object AdvanceHoldRequested : RatedSessionInput
    data object AdvanceHoldReleased : RatedSessionInput

    /** Voice answering resumes after a pause of its own. The microphone permission is already confirmed. */
    data object VoiceAnsweringResumed : RatedSessionInput

    /** The voice stack started again after a text-to-speech engine failure. */
    data object VoiceStackRestarted : RatedSessionInput
    data object PlaybackEngineUnavailable : RatedSessionInput

    /** The voice player's transport state changed. */
    data class PlaybackChanged(val playback: VoicePlaybackState) : RatedSessionInput
}
