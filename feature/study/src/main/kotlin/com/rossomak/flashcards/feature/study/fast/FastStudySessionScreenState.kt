package com.rossomak.flashcards.feature.study.fast

import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog

/**
 * Everything a Fast Study Session screen renders. No Study Mode field — the type itself is the
 * mode
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)).
 *
 * Deliberately duplicates the shape of `RatedStudySessionScreenState` rather than sharing a base
 * type with it: Rated carries an Attempt indicator, a completed counter and the Voice Answering
 * round, and a shared base would need a `when` on mode to stay useful — exactly the branching this
 * split exists to remove.
 */
data class FastStudySessionScreenState(
    val categoryName: String = "",
    val subcategoryNameById: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val flashcards: List<Flashcard> = emptyList(),
    val currentCardIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    @param:StringRes val error: Int? = null,
    val isReadAloudMode: Boolean = false,
    val isVoiceActive: Boolean = false,
    val isVoicePlaying: Boolean = false,
    // False at the last card's answer: the session ends only once that answer has been read.
    val isReadAloudNextAvailable: Boolean = true,
    // A voice engine could not start. Read-aloud stays on, never falling back to tap-through, and
    // the play control stays enabled to restart it.
    val isVoiceEngineUnavailable: Boolean = false,
    val activeDialog: StudySessionDialog? = null,
) {
    val currentCard: Flashcard? get() = flashcards.getOrNull(currentCardIndex)
}
