package com.rossomak.flashcards.core.domain.model

/**
 * A [RecentSession] with its Category and Subcategories resolved live.
 *
 * @param subcategories exactly one for SingleSubcategory; the ids that still resolve, possibly none, for
 * Custom; always empty for Quick, whose replay samples the Category again.
 */
data class RecentItem(
    val session: RecentSession,
    val category: Category,
    val subcategories: List<Subcategory>,
)
