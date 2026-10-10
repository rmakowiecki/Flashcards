package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.RecentItem

/** What Home renders below its header, derived by [HomeScreenState.body]. */
sealed interface HomeBody {

    /** A section is still loading and the reveal ceiling has not elapsed, so nothing renders yet. */
    data object Resolving : HomeBody

    /** Both sections loaded and the User has neither Favorites nor Recents. */
    data object FirstSession : HomeBody

    /** A section failed and none has content: an error with Retry, never the first-session prompt. */
    data object LoadError : HomeBody

    /** What each area renders, Favorites above Recents. */
    data class Sections(val favorites: HomeFavoritesArea, val recents: HomeRecentsArea) : HomeBody
}

/** The Favorites area of [HomeBody.Sections]. */
sealed interface HomeFavoritesArea {

    data class Carousel(val items: List<FavoriteItem>) : HomeFavoritesArea

    /** No Favorites next to Recents and the User has not hidden the hint: explains how to add one. */
    data object Hint : HomeFavoritesArea

    /** Nothing renders: Favorites failed, are still loading after the ceiling, or the hint is hidden. */
    data object Omitted : HomeFavoritesArea
}

/** The Recents area of [HomeBody.Sections]. */
sealed interface HomeRecentsArea {

    data class Rows(val items: List<RecentItem>) : HomeRecentsArea

    /** No Recents next to Favorites: says where sessions will appear. */
    data object Placeholder : HomeRecentsArea

    /** Nothing renders: Recents failed or are still loading after the ceiling. */
    data object Omitted : HomeRecentsArea
}
