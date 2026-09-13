package com.rossomak.flashcards.feature.browse

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory

/**
 * @param selectedSubcategoryIds **one nullable field, not a boolean plus a set.** `null` means
 * default mode; a set (possibly empty) means Selection Mode, so "not in selection mode but three
 * topics selected" is unrepresentable — the same discipline that makes
 * [SubcategoryDetailsContentState] sealed rather than a loading flag plus a nullable error plus a
 * list.
 * @param isFavorite deliberately fake — see [CategoryDetailsViewModel.onFavoriteToggle].
 * @param progressSummary the User's per-Subcategory progress rollup (ADR-0016), `null` until
 * [isProgressResolved] — and possibly still `null` after, for a User who has never finished a
 * session. Never read directly by the screen; go through [progressFor].
 * @param isProgressResolved `false` until the summary read completes, success or failure alike — a
 * failed read leaves it `false` forever rather than surfacing an error, per the ticket's "no error,
 * no retry prompt" rule. The topic list itself never waits on this: [subcategories] renders as soon
 * as it loads, independent of [isLoading].
 */
data class CategoryDetailsScreenState(
    val categoryId: String = "",
    val categoryName: String = "",
    val isLoading: Boolean = false,
    val subcategories: List<Subcategory> = emptyList(),
    val error: String? = null,
    val selectedSubcategoryIds: Set<String>? = null,
    val isFavorite: Boolean = false,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
) {

    val isSelectionMode: Boolean
        get() = selectedSubcategoryIds != null

    val selectedCount: Int
        get() = selectedSubcategoryIds?.size ?: 0

    /** Sum of [Subcategory.cardCount] across the selected Subcategories — the CTA's session size. */
    val selectedCardCount: Int
        get() {
            val selectedIds = selectedSubcategoryIds ?: return 0
            return subcategories.filter { it.id in selectedIds }.sumOf { it.cardCount }
        }

    /** False for an empty Category, so select-all never claims everything is selected when nothing exists. */
    val isAllSelected: Boolean
        get() = subcategories.isNotEmpty() && selectedCount == subcategories.size

    /**
     * One topic's ring/subtitle data, per ADR-0016's "two measures": [TopicProgress.Resolved.studiedCount]
     * over the topic's card count is what the ring draws and the subtitle names.
     * [TopicProgress.Resolved.masteredCount] isn't shown by the UI today — kept for a future use of
     * it — so it round-trips through this type unread by CategoryDetailsScreen. A topic absent from
     * [progressSummary] — or a User with no summary document at all — resolves to all-zero, the
     * same normal (not "unknown") rendering as any other topic.
     */
    fun progressFor(subcategoryId: String): TopicProgress {
        if (!isProgressResolved) return TopicProgress.Unresolved
        val summary = progressSummary?.subcategories?.get(subcategoryId)
        return TopicProgress.Resolved(
            studiedCount = summary?.studiedCount ?: 0,
            masteredCount = summary?.masteredCount ?: 0,
        )
    }
}

/** See [CategoryDetailsScreenState.progressFor]. */
sealed interface TopicProgress {
    /** The summary read hasn't resolved yet, or failed — renders as an unknown ring and dashes. */
    data object Unresolved : TopicProgress

    data class Resolved(val studiedCount: Int, val masteredCount: Int) : TopicProgress
}
