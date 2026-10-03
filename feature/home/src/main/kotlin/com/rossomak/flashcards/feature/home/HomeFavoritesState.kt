package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem

/** What Home's Favorites area shows. [Loading] and [Hidden] render nothing. */
sealed interface HomeFavoritesState {
    data object Loading : HomeFavoritesState
    data object Hidden : HomeFavoritesState
    data class Content(val items: List<FavoriteItem>) : HomeFavoritesState // Newest favorite first; never empty.
}
