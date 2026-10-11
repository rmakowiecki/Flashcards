package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.RecentItem

/**
 * What Home knows about the User's Recents. [Empty] means the User has none; [Failed] means they could not be
 * loaded, which is never read as "none". [HomeScreenState.body] decides what each state renders.
 */
sealed interface HomeRecentsState {
    data object Loading : HomeRecentsState
    data object Empty : HomeRecentsState
    data object Failed : HomeRecentsState
    data class Content(val items: List<RecentItem>) : HomeRecentsState // Newest first; never empty.
}
