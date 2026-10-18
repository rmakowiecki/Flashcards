package com.rossomak.flashcards.core.ui.composables

/**
 * One slot of a [FlashcardsAttemptIndicator]: a graded outcome, the in-progress attempt, or an
 * attempt not yet reached. Named by outcome rather than by color so the enum still reads correctly
 * if the palette changes.
 */
enum class FlashcardsAttemptSlotState {
    /** Rated "Not at all" — filled with [com.rossomak.flashcards.core.designsystem.theme.SemanticColors.negativeContainer]. */
    Failed,

    /** Rated "Somewhat" — filled with [com.rossomak.flashcards.core.designsystem.theme.SemanticColors.neutralContainer]. */
    Partial,

    /** Rated "Very well" — filled with [com.rossomak.flashcards.core.designsystem.theme.SemanticColors.positiveContainer]. */
    Correct,

    /** The attempt in progress — shown up to but not answered yet, so there is no rating to fill it with. */
    Current,

    /** An attempt not yet reached. */
    Future,
}
