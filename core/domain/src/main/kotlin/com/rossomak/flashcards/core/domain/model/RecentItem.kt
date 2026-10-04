package com.rossomak.flashcards.core.domain.model

/**
 * A [RecentSession] with its Category looked up for color and icon only; names come from the session.
 *
 * @param category `null` when the Category could not be read, so the row shows without its styling.
 */
data class RecentItem(
    val session: RecentSession,
    val category: Category?,
)
