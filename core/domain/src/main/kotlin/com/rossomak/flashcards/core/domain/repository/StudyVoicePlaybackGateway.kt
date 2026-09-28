package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The study session's voice player: reads cards aloud, speaks notices on a separate voice, and
 * reports transport commands from outside the app instead of acting on them. It makes no session
 * decision; the study session coordinators do.
 */
interface StudyVoicePlaybackGateway {
    val state: StateFlow<VoicePlaybackState>

    /**
     * Unconflated, in the order things happened. Single collector: an event is delivered once,
     * including one reported before collection started. Named apart from
     * [VoiceCaptureGateway.captureEvents] so one implementation can serve both.
     */
    val playbackEvents: Flow<PlaybackEvent>

    /**
     * Starts the voice stack and reads [cards] from [startIndex]. [isVoiceAnsweringSession] is fixed
     * for the whole session and keeps the microphone foreground-service type held from the first
     * question to [stop], even while voice answering is paused, so a background resume can still
     * listen.
     */
    fun start(cards: List<Flashcard>, startIndex: Int, sessionTitle: String, isVoiceAnsweringSession: Boolean)

    /**
     * Swaps in a new card order without touching the utterance in flight. [cards]'s head becomes the
     * player's current card.
     */
    fun updateQueue(cards: List<Flashcard>)

    /** Stops the voice stack. Synchronous and safe to call more than once. */
    fun stop()

    /** Starts or resumes reading. Clears an auto-resume pending from a transient audio-focus loss. */
    fun play()

    /** Pauses reading. Clears an auto-resume pending from a transient audio-focus loss. */
    fun pause()
    fun moveToNextCard()
    fun moveToPreviousCard()
    fun jumpTo(index: Int)
    fun restartCurrentCard()
    fun showAnswer()

    /** Reads the question of the head card, or stops when there is none. */
    fun advanceAfterVoiceAnswer()

    /**
     * While [closed], the player stops on the current card at the end of the pause after its answer,
     * instead of moving on, and reports [PlaybackEvent.AdvanceGateReached].
     */
    fun setAdvanceGate(closed: Boolean)

    /** In question-only mode the player stops after each question and reports [PlaybackEvent.QuestionFinished]. */
    fun setQuestionOnlyMode(enabled: Boolean)

    /** Applies to the questions and answers, and to the spoken notices. */
    fun setSpeechRate(rate: Float)

    /** Applies to the questions and answers, and to the spoken notices. */
    fun setVoice(voiceId: String?)

    /** Queues [notice] on the notice voice. Always answered by exactly one [PlaybackEvent.NoticeFinished]. */
    fun speakNotice(notice: SpokenNotice)
}
