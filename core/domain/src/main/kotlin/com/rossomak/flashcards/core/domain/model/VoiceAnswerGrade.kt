package com.rossomak.flashcards.core.domain.model

/**
 * Result of grading a spoken answer: the PII-stripped, disfluency-normalized transcript plus
 * the LLM's completeness grade and feedback. This is the only artifact of a spoken answer that
 * is ever persisted — never audio, never an un-sanitized transcript.
 *
 * @param feedback what the panel shows; may hold single-backtick inline code spans. Empty only for
 * a correct answer that missed nothing.
 * @param spokenFeedback the same feedback worded for speech, with no markup. Empty exactly when
 * [feedback] is.
 */
data class VoiceAnswerGrade(
    val sanitizedTranscript: String,
    val gradePercent: Int,
    val feedback: String,
    val spokenFeedback: String,
)

/**
 * Maps [VoiceAnswerGrade.gradePercent] onto the same three-valued [FlashcardAttemptRating] a manual tap
 * produces — the fixed grade-band mapping (Failed < 30, Partial 30-74, Correct >= 75) from
 * [ADR-0031](../../../../../../../docs/adr/0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md),
 * unified onto the Rating pipeline by
 * [ADR-0026](../../../../../../../docs/adr/0026-voice-grade-unifies-with-rating-reveal-tied-to-speech-end.md).
 * The server's grading prompt states the same bands, so change both together.
 */
fun VoiceAnswerGrade.toFlashcardAttemptRating(): FlashcardAttemptRating = when {
    gradePercent >= VOICE_GRADE_CORRECT_THRESHOLD -> FlashcardAttemptRating.Correct
    gradePercent >= VOICE_GRADE_PARTIAL_THRESHOLD -> FlashcardAttemptRating.PartiallyCorrect
    else -> FlashcardAttemptRating.Failed
}

private const val VOICE_GRADE_PARTIAL_THRESHOLD = 30
private const val VOICE_GRADE_CORRECT_THRESHOLD = 75
