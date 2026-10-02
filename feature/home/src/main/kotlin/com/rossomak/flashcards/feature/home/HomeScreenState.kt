package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.ProgressSummary

/**
 * @param progressSummary the User's per-Subcategory counts; `null` until a summary document exists.
 * @param isProgressResolved whether the summary read has finished. `false` renders every Subcategory's
 * progress as unknown, which is not the same as a resolved zero.
 */
data class HomeScreenState(
    val favoriteItems: List<FavoriteItem> = emptyList(),
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
)
