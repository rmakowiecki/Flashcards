package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The study session's voice player: presents one part of one card when told to and reports when
 * that part was read in full, speaks notices on a separate voice, and reports transport commands from
 * outside the app instead of acting on them. It never moves on to another part or card by itself and
 * makes no session decision; the study session coordinators do.
 */
@Suppress("TooManyFunctions") // one method per command of the voice stack.
interface StudyVoicePlaybackGateway {
    val state: StateFlow<VoicePlaybackState>

    /**
     * Unconflated, in the order things happened. Single collector: an event is delivered once,
     * including one reported before collection started. Named apart from
     * [VoiceCaptureGateway.captureEvents] so one implementation can serve both.
     */
    val playbackEvents: Flow<PlaybackEvent>

    /**
     * Starts the voice stack and reads the question of [cards] at [startIndex]. [isVoiceAnsweringSession] is fixed
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

    /** Starts or resumes reading the part being presented, from its start. */
    fun play()

    /** Pauses reading. The player never resumes on its own; only [play] does. */
    fun pause()

    /**
     * Presents card [index] at its question and, while playing, reads it aloud; while paused it only
     * shows it. An [index] outside the list stops playing. Answered by
     * [PlaybackEvent.QuestionFinished] once the question was read in full.
     */
    fun presentQuestion(index: Int)

    /**
     * Presents card [index] at its answer, reports [PlaybackEvent.AnswerRevealed] and, while playing,
     * reads it aloud; while paused it only shows it. Answered by [PlaybackEvent.AnswerFinished] once
     * the answer was read in full.
     */
    fun presentAnswer(index: Int)

    /** Applies to the questions and answers, and to the spoken notices, from the next utterance on. */
    fun setSpeechRate(rate: Float)

    /** Applies to the questions and answers, and to the spoken notices, from the next utterance on. */
    fun setVoice(voiceId: String?)

    /**
     * Queues [notice] on the notice voice. Answered by exactly one [PlaybackEvent.NoticeFinished],
     * except a feedback cut by [stopFeedback].
     */
    fun speakNotice(notice: SpokenNotice)

    /**
     * Cuts the [SpokenNotice.Feedback] being spoken and forgets it: it never reports finished, so
     * a replay started right after can never be mistaken for it.
     */
    fun stopFeedback()

    /**
     * Reports the player playing again without reading anything, while the voice round goes on
     * elsewhere (grading, the grading feedback). The next read continues from there.
     */
    fun resumeWithoutReading()

    /**
     * The commands the system transport controls (notification, headset, lock screen) offer from
     * now on. A command outside [commands] is neither offered nor reported.
     */
    fun setAvailableCommands(commands: Set<TransportCommandType>)

    /**
     * Shows the session's own progress, [completedCount] of [totalCount] cards, in the system
     * media controls, instead of the player's position in its list.
     */
    fun setSessionProgress(completedCount: Int, totalCount: Int)
}
