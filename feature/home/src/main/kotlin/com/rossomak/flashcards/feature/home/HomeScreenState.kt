package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.ProgressSummary

/**
 * @param favorites what the Favorites area shows; [HomeFavoritesState.Loading] until the first emission.
 * @param recents what the Recently studied area shows; [HomeRecentsState.Loading] until the first emission.
 * @param progressSummary the User's per-Subcategory counts; `null` until a summary document exists.
 * @param isProgressResolved whether the summary read has finished. `false` renders every Subcategory's
 * progress as unknown, which is not the same as a resolved zero.
 */
data class HomeScreenState(
    val favorites: HomeFavoritesState = HomeFavoritesState.Loading,
    val recents: HomeRecentsState = HomeRecentsState.Loading,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
)
