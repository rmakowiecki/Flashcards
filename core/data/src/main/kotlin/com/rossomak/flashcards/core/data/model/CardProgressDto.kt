package com.rossomak.flashcards.core.data.model

import com.google.firebase.Timestamp

data class SubcategoryProgressDetailsDto(
    val categoryId: String = "",
    val cards: Map<String, CardProgressEntryDto> = emptyMap(),
)

data class CardProgressEntryDto(
    val state: String = "",
    val firstStudiedAt: Timestamp? = null,
    val masteredAt: Timestamp? = null,
)
