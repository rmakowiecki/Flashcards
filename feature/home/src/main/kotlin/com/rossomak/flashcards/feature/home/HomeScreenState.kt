package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem

data class HomeScreenState(
    val favoriteItems: List<FavoriteItem> = emptyList(),
)
