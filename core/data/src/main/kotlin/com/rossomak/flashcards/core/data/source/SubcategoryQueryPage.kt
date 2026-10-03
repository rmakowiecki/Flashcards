package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.SubcategoryDto

/**
 * One Subcategory query's documents, plus whether Firestore served them only from the on-device cache.
 * A document missing from a server answer is gone; one missing from a cache answer may never have
 * been cached.
 */
data class SubcategoryQueryPage(
    val subcategories: List<SubcategoryDto>,
    val isFromCache: Boolean,
)
