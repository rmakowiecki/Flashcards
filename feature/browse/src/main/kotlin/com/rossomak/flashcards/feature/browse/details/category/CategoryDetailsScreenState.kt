package com.rossomak.flashcards.feature.browse.details.category

import androidx.annotation.StringRes
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory

/**
 * @param selectedSubcategoryIds **one nullable field, not a boolean plus a set.** `null` means
 * default mode; a set (possibly empty) means Selection Mode, so "not in selection mode but three
 * subcategories  selected" is unrepresentable — the same discipline that makes
 * [com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState] sealed rather than a loading flag plus a nullable error plus a
 * list.
 * @param isFavorite deliberately fake — see [CategoryDetailsViewModel.onFavoriteToggle].
 * @param progressSummary the User's per-Subcategory progress rollup (ADR-0016), `null` until
 * [isProgressResolved] — and possibly still `null` after, for a User who has never finished a
 * session. Never read directly by the screen; go through [progressFor].
 * @param isProgressResolved `false` until the summary read completes, success or failure alike — a
 * failed read leaves it `false` forever rather than surfacing an error, per the ticket's "no error,
 * no retry prompt" rule. The subcategory list itself never waits on this: [subcategories] renders as soon
 * as it loads, independent of [isLoading].
 */
data class CategoryDetailsScreenState(
    val categoryId: String = "",
    val categoryName: String = "",
    val isLoading: Boolean = false,
    val subcategories: List<Subcategory> = emptyList(),
    @param:StringRes val errorResId: Int? = null,
    val selectedSubcategoryIds: Set<String>? = null,
    val isFavorite: Boolean = false,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
) {

    val isSelectionMode: Boolean
        get() = selectedSubcategoryIds != null

    val selectedCount: Int
        get() = selectedSubcategoryIds?.size ?: 0

    /** Sum of [Subcategory.cardCount] across the selected Subcategories — the CTA button session size. */
    val selectedCardCount: Int
        get() {
            val selectedIds = selectedSubcategoryIds ?: return 0
            return subcategories.filter { it.id in selectedIds }.sumOf { it.cardCount }
        }

    val isAllSelected: Boolean
        get() = subcategories.isNotEmpty() && selectedCount == subcategories.size

    /**
     * One subcategory's ring/subtitle data, per ADR-0016's "two measures": [SubcategoryProgress.Resolved.studiedCount]
     * over the subcategory's card count is what the ring draws and the subtitle names.
     * [SubcategoryProgress.Resolved.masteredCount] isn't shown by the UI today — kept for a future use of
     * it — so it round-trips through this type unread by CategoryDetailsScreen. A subcategory absent from
     * [progressSummary] — or a User with no summary document at all — resolves to all-zero, the
     * same normal (not "unknown") rendering as any other subcategory.
     */
    fun progressFor(subcategoryId: String): SubcategoryProgress = progressSummary.subcategoryProgressFor(subcategoryId, isProgressResolved)
}

/** See [CategoryDetailsScreenState.progressFor]. */
sealed interface SubcategoryProgress {
    /** The summary read hasn't resolved yet, or failed — renders as an unknown ring and dashes. */
    data object Unresolved : SubcategoryProgress

    data class Resolved(val studiedCount: Int, val masteredCount: Int) : SubcategoryProgress
}

/**
 * Resolves one subcategory's [SubcategoryProgress] against a per-user summary — shared by
 * [CategoryDetailsScreenState.progressFor] and [com.rossomak.flashcards.feature.browse.BrowseScreenState.progressFor] so Category Details'
 * rings and Browse's search-result rings agree on the same unresolved/absent-summary rules.
 */
fun ProgressSummary?.subcategoryProgressFor(subcategoryId: String, isResolved: Boolean): SubcategoryProgress {
    if (!isResolved) return SubcategoryProgress.Unresolved
    val summary = this?.subcategories?.get(subcategoryId)
    return SubcategoryProgress.Resolved(
        studiedCount = summary?.studiedCount ?: 0,
        masteredCount = summary?.masteredCount ?: 0,
    )
}
