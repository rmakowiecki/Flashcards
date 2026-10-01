package com.rossomak.flashcards.core.domain.session

import kotlin.time.Duration

/** What [FastStudySessionCoordinator] must do after a [FastSessionReducer] transition, in order. */
sealed interface FastSessionEffect {
    data object Play : FastSessionEffect
    data object PausePlayback : FastSessionEffect

    /** Present the question of the card at [index]; the player reads it while playing. */
    data class PresentQuestion(val index: Int) : FastSessionEffect

    /** Present the answer of the card at [index]; the player reads it while playing. */
    data class PresentAnswer(val index: Int) : FastSessionEffect

    /** Start the pause between a question and its answer; it ends in [FastSessionInput.QuestionPauseElapsed]. */
    data object StartQuestionPause : FastSessionEffect

    /** Start the pause after an answer; it ends in [FastSessionInput.AdvancePauseElapsed]. */
    data object StartAdvancePause : FastSessionEffect

    /** Stop the running read-aloud pause, if any, without it elapsing. */
    data object CancelReadAloudPause : FastSessionEffect

    /** Wait the release linger, then report [FastSessionInput.ReleaseLingerElapsed]. */
    data object StartReleaseLinger : FastSessionEffect
    data object CancelReleaseLinger : FastSessionEffect

    /** Start the voice stack again at [startIndex], after a text-to-speech engine failure. */
    data class RestartVoiceStack(val startIndex: Int) : FastSessionEffect
    data object StopVoiceStack : FastSessionEffect
    data class Emit(val event: FastSessionEvent) : FastSessionEffect

    /** The last card's answer has been shown or read in full; the session is over. */
    data object SessionComplete : FastSessionEffect

    /** Wait [duration], then report [FastSessionInput.BlipElapsed]. Starting again restarts the wait. */
    data class StartBlipTimer(val duration: Duration) : FastSessionEffect
    data object CancelBlipTimer : FastSessionEffect
}
