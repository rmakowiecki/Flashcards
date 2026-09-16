package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import com.rossomak.flashcards.core.domain.model.UserFavorites

fun UserFavoritesDto.toDomain(): UserFavorites = UserFavorites(
    categoryIds = categories.mapValues { (_, timestamp) -> timestamp.toInstant() },
    subcategoryIds = subcategories.mapValues { (_, timestamp) -> timestamp.toInstant() },
)
