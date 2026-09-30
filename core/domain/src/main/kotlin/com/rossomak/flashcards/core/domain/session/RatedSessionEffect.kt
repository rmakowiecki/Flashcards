package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.SpokenNotice

/** What [RatedStudySessionCoordinator] must do after a [RatedSessionReducer] transition, in order. */
sealed interface RatedSessionEffect {

    /** Hand the voice player the queue in its new order; the screen already shows it. */
    data class SyncQueue(val cards: List<Flashcard>) : RatedSessionEffect

    /** Present the question of the queue head, after any [SyncQueue]. The player reads it only while it plays. */
    data object PresentHeadQuestion : RatedSessionEffect

    /** Prepare the microphone, then open it; [RatedSessionInput.MicrophoneOpened] reports it recording. */
    data class OpenListeningWindow(val cardId: String) : RatedSessionEffect

    /** The microphone records: start the silence timer. */
    data object StartSilenceTimer : RatedSessionEffect

    /** Tell the user, by sound, that the microphone now records. */
    data object PlayListeningCue : RatedSessionEffect
    data object StopListening : RatedSessionEffect
    data object CancelSilenceTimer : RatedSessionEffect

    /** Transcribe and grade the answer to [card]. The bytes are only ever handed to that call. */
    class Grade(val card: Flashcard, val obfuscatedWav: ByteArray) : RatedSessionEffect
    data object CancelGrading : RatedSessionEffect
    data class SpeakNotice(val notice: SpokenNotice) : RatedSessionEffect

    /** Wait the notice tail, then report [RatedSessionInput.NoticeTailElapsed]. */
    data object StartNoticeTail : RatedSessionEffect
    data object CancelNoticeTail : RatedSessionEffect

    /** Wait the release linger, then report [RatedSessionInput.ReleaseLingerElapsed]. */
    data object StartReleaseLinger : RatedSessionEffect
    data object CancelReleaseLinger : RatedSessionEffect

    /** Cut the grading feedback being spoken, without it ever reporting finished. */
    data object StopFeedback : RatedSessionEffect
    data object PausePlayback : RatedSessionEffect
    data object Play : RatedSessionEffect

    /** Mark the player playing again without reading anything: the round goes on on another voice. */
    data object ResumeWithoutReading : RatedSessionEffect
    data object StopVoiceAnswering : RatedSessionEffect
    data object StartVoiceAnswering : RatedSessionEffect

    /** Stop the whole voice stack: player, notices and microphone. */
    data object StopVoiceStack : RatedSessionEffect

    /** Start the player again at the queue head, after an engine failure stopped it. */
    data object RestartVoiceStack : RatedSessionEffect

    /** The microphone permission is gone: stop voice, tell the user, then end the session as abandoned. */
    data object EndForRevokedMicPermission : RatedSessionEffect
    data class Emit(val event: RatedSessionEvent) : RatedSessionEffect

    /** Every card has reached a Terminal State. */
    data object SessionComplete : RatedSessionEffect
}
