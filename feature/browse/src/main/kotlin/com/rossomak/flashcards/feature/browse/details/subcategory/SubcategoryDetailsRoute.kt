package com.rossomak.flashcards.feature.browse.details.subcategory

import kotlinx.serialization.Serializable

@Serializable
data class SubcategoryDetailsRoute(
    val categoryId: String,
    val categoryName: String,
    val subcategoryId: String,
    val subcategoryName: String,
)
