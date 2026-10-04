package com.rossomak.flashcards.feature.study.fast

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.TransportCommandType
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
    val isReadAloudMode: Boolean = false,
    val isVoiceActive: Boolean = false,
    val isVoicePlaying: Boolean = false,
    // A voice engine could not start. Read-aloud stays on, never falling back to tap-through, and
    // the play control stays enabled to restart it.
    val isVoiceEngineUnavailable: Boolean = false,
    // What the transport row may offer: the same set the notification and a headset get.
    val availableTransportCommands: Set<TransportCommandType> = TransportCommandType.entries.toSet(),
    val activeDialog: StudySessionDialog? = null,
) {
    val currentCard: Flashcard? get() = flashcards.getOrNull(currentCardIndex)
}
