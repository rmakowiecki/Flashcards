package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.RecentItem

/** What Home's Recently studied area shows. [Loading] and [Hidden] render nothing. */
sealed interface HomeRecentsState {
    data object Loading : HomeRecentsState
    data object Hidden : HomeRecentsState
    data class Content(val items: List<RecentItem>) : HomeRecentsState // Newest first; never empty.
}
