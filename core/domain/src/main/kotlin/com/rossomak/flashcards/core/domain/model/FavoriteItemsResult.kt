package com.rossomak.flashcards.core.domain.model

/** One emission of [ObserveFavoriteItemsUseCase][com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase]. */
sealed interface FavoriteItemsResult {

    /** Every Favorite id was fetched; [items] is newest first and empty only when the User has no Favorites. */
    data class Resolved(val items: List<FavoriteItem>) : FavoriteItemsResult

    /** Favorite ids exist but a fetch ran out of retries, so some or all of the items are missing. */
    data object Unresolved : FavoriteItemsResult
}
