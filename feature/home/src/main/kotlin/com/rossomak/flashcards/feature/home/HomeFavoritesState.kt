package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem

/**
 * What Home knows about the User's Favorites. [Empty] means the User has none; [Failed] means they could not be
 * loaded, which is never read as "none". [HomeScreenState.body] decides what each state renders.
 */
sealed interface HomeFavoritesState {
    data object Loading : HomeFavoritesState
    data object Empty : HomeFavoritesState
    data object Failed : HomeFavoritesState
    data class Content(val items: List<FavoriteItem>) : HomeFavoritesState // Newest favorite first; never empty.
}
