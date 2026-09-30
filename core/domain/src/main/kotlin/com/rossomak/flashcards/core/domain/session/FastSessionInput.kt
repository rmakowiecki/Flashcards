package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.VoicePlaybackState

/** Everything that can happen to a Fast Study Session, as [FastSessionReducer] takes it in. */
sealed interface FastSessionInput {

    /** The answer of [cardId] was revealed: by hand, or by the player presenting it. */
    data class AnswerRevealed(val cardId: String) : FastSessionInput

    /** The voice player's transport state changed. Only whether it plays is read from it. */
    data class PlaybackChanged(val playback: VoicePlaybackState) : FastSessionInput
    data object PlaybackEngineUnavailable : FastSessionInput

    /** The player read the question of [cardId] in full. */
    data class QuestionFinished(val cardId: String) : FastSessionInput

    /** The player read the answer of [cardId] in full. */
    data class AnswerFinished(val cardId: String) : FastSessionInput

    /** The pause between the presented card's question and its answer is over. */
    data object QuestionPauseElapsed : FastSessionInput

    /** The pause after the presented card's answer is over: the auto-advance point. */
    data object AdvancePauseElapsed : FastSessionInput

    /** Play, from the app or from outside it. Also restarts the voice stack after an engine failure. */
    data object PlayRequested : FastSessionInput

    /** Pause, from the app or from outside it. */
    data object PauseRequested : FastSessionInput

    /**
     * Next, from the app or from outside it. With read-aloud on it steps through the card: at a
     * question it presents the answer, at an answer it moves on. In a tap-through session it advances
     * to the next card once the answer shows.
     */
    data object NextRequested : FastSessionInput

    /** Restart the presented card when [restartsCard], go back one card otherwise. */
    data class PreviousRequested(val restartsCard: Boolean) : FastSessionInput

    /** Jump to the card at [index], from outside the app. */
    data class JumpRequested(val index: Int) : FastSessionInput
    data object TemporaryPauseRequested : FastSessionInput
    data object TemporaryPauseEnded : FastSessionInput
    data object AdvanceHoldRequested : FastSessionInput
    data object AdvanceHoldReleased : FastSessionInput

    /** The release linger ran in full. */
    data object ReleaseLingerElapsed : FastSessionInput
}
