package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem

/** What the Favorites area of Home shows. [Content] is only ever built from a non-empty list. */
sealed interface HomeFavoritesState {

    /** Nothing resolved yet; renders nothing, so a User with Favorites never sees the empty state flash. */
    data object Loading : HomeFavoritesState

    /** The User has no Favorites. */
    data object Empty : HomeFavoritesState

    /** The User's Favorites, most recently favorited first. Never empty: an empty list is [Empty]. */
    data class Content(val items: List<FavoriteItem>) : HomeFavoritesState
}
