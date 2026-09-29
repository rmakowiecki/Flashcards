package com.rossomak.flashcards.core.domain.model

/**
 * What a Study Session ViewModel needs at load time, bundled from
 * [com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase]'s parallel fan-out over
 * the routed Subcategories.
 *
 * The two card-progress-adjacent halves degrade independently, on purpose: [flashcardsResult] fails
 * the whole session (no cards, nothing to study), while [priorProgressByCardId] never does — a
 * failed or never-studied Subcategory's progress read simply contributes no entries, same as a
 * genuinely absent one, rather than blocking the session or surfacing an error.
 */
data class SessionStartData(
    val flashcardsResult: Result<List<Flashcard>>,
    /** cardId -> its packed progress entry, merged across every Subcategory in scope; a missing key means new. */
    val priorProgressByCardId: Map<String, CardProgressEntry>,
)
