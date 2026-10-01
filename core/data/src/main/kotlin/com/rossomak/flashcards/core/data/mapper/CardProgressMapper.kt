package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDetailsDto
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails

/** A card entry with no recognizable [FlashcardStudyProgressState] or no [CardProgressEntryDto.firstStudiedAt] is dropped, not crashed on. */
fun SubcategoryProgressDetailsDto.toDomain(subcategoryId: String): SubcategoryProgressDetails = SubcategoryProgressDetails(
    subcategoryId = subcategoryId,
    categoryId = categoryId,
    cards = cards.mapNotNull { (cardId, dto) -> dto.toDomain()?.let { entry -> cardId to entry } }.toMap(),
)

private fun CardProgressEntryDto.toDomain(): CardProgressEntry? {
    val resolvedState = runCatching { FlashcardStudyProgressState.valueOf(state) }.getOrNull() ?: return null
    val resolvedFirstStudiedAt = firstStudiedAt?.toInstant() ?: return null
    return CardProgressEntry(
        state = resolvedState,
        firstStudiedAt = resolvedFirstStudiedAt,
        masteredAt = masteredAt?.toInstant(),
    )
}
