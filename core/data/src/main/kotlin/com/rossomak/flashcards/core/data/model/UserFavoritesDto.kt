package com.rossomak.flashcards.core.data.model

import com.google.firebase.Timestamp

data class UserFavoritesDto(
    val categories: Map<String, Timestamp> = emptyMap(),
    val subcategories: Map<String, Timestamp> = emptyMap(),
)
