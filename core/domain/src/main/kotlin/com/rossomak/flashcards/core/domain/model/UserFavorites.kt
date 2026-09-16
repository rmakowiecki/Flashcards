package com.rossomak.flashcards.core.domain.model

import java.time.Instant

/**
 * A user's full favorites state — categories and subcategories are independent sets, one never
 * implies or cascades into the other. Backed by a single Firestore document
 * (`users/{uid}/favorites/state`, see firestore-schema.md) so observing or writing either set costs
 * one read/write regardless of how many ids are favorited. Each id maps to the instant it was
 * favorited, so consumers (e.g. a "recently favorited" home carousel) can sort by recency.
 */
data class UserFavorites(
    val categoryIds: Map<String, Instant>,
    val subcategoryIds: Map<String, Instant>,
) {
    companion object {
        val EMPTY = UserFavorites(categoryIds = emptyMap(), subcategoryIds = emptyMap())
    }
}
