package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import kotlin.time.ComparableTimeMark

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

    /** The release linger ran in full. */
    data object ReleaseLingerElapsed : RatedSessionInput

    /**
     * Play while the session is paused by an engine failure or by voice answering itself.
     * [isMicrophoneGranted] is the permission read just before; a session without voice answering
     * needs no microphone and always passes `true`.
     */
    data class ResumeRequested(val isMicrophoneGranted: Boolean) : RatedSessionInput
    data object PlaybackEngineUnavailable : RatedSessionInput

    /** The voice player's transport state changed. */
    data class PlaybackChanged(val playback: VoicePlaybackState) : RatedSessionInput

    /** Something changed in the audio around the session, stamped [at] when the coordinator received it. */
    data class AudioEnvironmentChanged(val signal: AudioEnvironmentSignal, val at: ComparableTimeMark) : RatedSessionInput

    /** A blip lasted as long as [StartBlipTimer][RatedSessionEffect.StartBlipTimer] asked. */
    data object BlipElapsed : RatedSessionInput

    /** The capture gate tail after the end of a blip ran in full. */
    data object GateTailElapsed : RatedSessionInput
}
