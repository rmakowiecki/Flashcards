package com.rossomak.flashcards.core.domain.model

/**
 * A short announcement spoken during a Voice Answering round, on its own voice so it never
 * interferes with the question playback. Non-string: the voice layer resolves each variant to its
 * text. Every variant except [Feedback] is a short notice.
 */
sealed interface SpokenNotice {

    /** The grade: the Rating's name and the grader's [rationale]. Never the percentage. */
    data class Feedback(val rating: FlashcardAttemptRating, val rationale: String) : SpokenNotice

    /** Nothing was heard; the card is put back and the session moves on. */
    data object SilenceSkip : SpokenNotice

    /** Nothing was heard again; voice answering pauses. */
    data object SilencePause : SpokenNotice

    /** The answer could not be graded, for [reason]; the card is put back and the session moves on. */
    data class GradingFailed(val reason: GradingFailureReason) : SpokenNotice

    /** Grading failed again; voice answering pauses. */
    data object GradingPause : SpokenNotice

    /** The microphone stopped working; voice answering pauses on the current card. */
    data object CaptureFailed : SpokenNotice
}

/** `true` for every notice that is not [SpokenNotice.Feedback]. */
val SpokenNotice.isShort: Boolean get() = this !is SpokenNotice.Feedback
