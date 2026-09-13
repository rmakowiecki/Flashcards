package com.rossomak.flashcards.feature.browse.details.category

import kotlinx.serialization.Serializable

@Serializable
data class CategoryDetailsRoute(val categoryId: String, val categoryName: String)
