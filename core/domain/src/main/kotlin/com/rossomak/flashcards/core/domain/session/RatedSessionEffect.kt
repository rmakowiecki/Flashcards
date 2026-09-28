package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.SpokenNotice

/** What [RatedStudySessionCoordinator] must do after a [RatedSessionReducer] transition, in order. */
sealed interface RatedSessionEffect {

    /** Hand the voice player the queue in its new order; the screen already shows it. */
    data class SyncQueue(val cards: List<Flashcard>) : RatedSessionEffect
    data object AdvanceAfterVoiceAnswer : RatedSessionEffect

    /** Wait for a capturable route, open the microphone, then start the silence timer. */
    data class OpenListeningWindow(val cardId: String) : RatedSessionEffect
    data object StopListening : RatedSessionEffect
    data object CancelSilenceTimer : RatedSessionEffect

    /** Transcribe and grade the answer to [card]. The bytes are only ever handed to that call. */
    class Grade(val card: Flashcard, val obfuscatedWav: ByteArray) : RatedSessionEffect
    data object CancelGrading : RatedSessionEffect
    data class SpeakNotice(val notice: SpokenNotice) : RatedSessionEffect

    /** Wait the notice tail, then report [RatedSessionInput.NoticeTailElapsed]. */
    data object StartNoticeTail : RatedSessionEffect
    data object PausePlayback : RatedSessionEffect
    data object Play : RatedSessionEffect
    data object RestartCurrentCard : RatedSessionEffect
    data object StopVoiceAnswering : RatedSessionEffect
    data object StartVoiceAnswering : RatedSessionEffect

    /** Stop the whole voice stack: player, notices and microphone. */
    data object StopVoiceStack : RatedSessionEffect
    data class Emit(val event: RatedSessionEvent) : RatedSessionEffect

    /** Every card has reached a Terminal State. */
    data object SessionComplete : RatedSessionEffect
}
