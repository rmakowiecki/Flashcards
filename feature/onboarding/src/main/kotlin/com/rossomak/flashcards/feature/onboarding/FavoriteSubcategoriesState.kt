package com.rossomak.flashcards.feature.onboarding

import com.rossomak.flashcards.feature.onboarding.model.FavoriteSubcategoryOption
import kotlinx.collections.immutable.ImmutableList

/**
 * What the Favorites step knows about its curated list. The list is fetched when the step is first
 * reached, so [Loading] is also the state before that fetch starts. [Failed] covers a failed fetch
 * and an empty list alike: the server always populates the list, so an empty one means the read went wrong.
 */
sealed interface FavoriteSubcategoriesState {
    data object Loading : FavoriteSubcategoriesState
    data object Failed : FavoriteSubcategoriesState
    data class Content(val options: ImmutableList<FavoriteSubcategoryOption>) : FavoriteSubcategoriesState // Never empty.
}
