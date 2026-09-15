package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import com.rossomak.flashcards.core.domain.model.UserFavorites

fun UserFavoritesDto.toDomain(): UserFavorites = UserFavorites(
    categoryIds = categories.keys,
    subcategoryIds = subcategories.keys,
)
