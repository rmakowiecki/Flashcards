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

    /** What each area renders; an empty list renders nothing for that area. */
    data class Sections(val favoriteItems: List<FavoriteItem>, val recentItems: List<RecentItem>) : HomeBody
}
