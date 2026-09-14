package com.rossomak.flashcards.core.domain.model

/**
 * A user's full favorites state — categories and subcategories are independent sets, one never
 * implies or cascades into the other. Backed by a single Firestore document
 * (`users/{uid}/favorites/state`, see firestore-schema.md) so observing or writing either set costs
 * one read/write regardless of how many ids are favorited.
 */
data class UserFavorites(
    val categoryIds: Set<String>,
    val subcategoryIds: Set<String>,
) {
    companion object {
        val EMPTY = UserFavorites(categoryIds = emptySet(), subcategoryIds = emptySet())
    }
}
