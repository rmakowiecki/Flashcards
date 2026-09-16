package com.rossomak.flashcards.feature.browse

import androidx.compose.runtime.Immutable
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CategorySearchResults
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress
import com.rossomak.flashcards.feature.browse.details.category.subcategoryProgressFor

sealed interface BrowseNavigationDestination : NavigationEvent {
    data class CategoryDetails(val categoryId: String, val categoryName: String) : BrowseNavigationDestination

    data class SubcategoryDetails(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : BrowseNavigationDestination

    /** Straight from a search result's play button into Study Creation, skipping Subcategory Details. */
    data class PreviewStudySession(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : BrowseNavigationDestination
}

/**
 * The search field's callbacks travel together as one parameter — they are a single concern
 * and always wired to the same ViewModel. Marked [Immutable] so Compose treats the holder as
 * stable; callers should `remember` it rather than rebuilding it on every recomposition.
 *
 * There is no `onClear`: the Material 3 search field owns its own text, so clearing it is a
 * `TextFieldState` operation that reaches the ViewModel through [onQueryChange] like any other
 * edit.
 */
@Immutable
data class BrowseSearchActions(
    val onQueryChange: (String) -> Unit,
    val onActivate: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The one thing the expanded search bar renders, replacing a nullable-results-plus-error-flag pair
 * so illegal combinations (an error alongside stale results, say) can't be represented. Mirrors
 * [com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState]'s flat-sealed style in
 * this same feature.
 *
 * [NoMatch] is deliberately distinct from [Error]: a query that ran cleanly and matched nothing is
 * a successful, empty result, not a failure — conflating the two would make "no matches" read as
 * something having gone wrong. [Results] never carries an empty [CategorySearchResults]; an empty
 * one is always [NoMatch] instead, so call sites never need an `isEmpty` check of their own.
 */
sealed interface SearchStatus {
    /** No query typed yet, or one shorter than [com.rossomak.flashcards.core.domain.usecase.SearchCategoriesUseCase.MIN_QUERY_LENGTH]. */
    data object Prompt : SearchStatus

    /** A long-enough query is queued or running — covers the debounce wait and the fetch itself. */
    data object Loading : SearchStatus

    data class Results(val results: CategorySearchResults) : SearchStatus

    /** The query ran and matched nothing. */
    data object NoMatch : SearchStatus

    /** The query failed to run at all (e.g. no connectivity). */
    data object Error : SearchStatus
}

/**
 * [isSearchActive] records whether search is open. The expanded/collapsed state the UI actually
 * renders from is Material 3's own `SearchBarState`; this mirrors it so the ViewModel can clear the
 * query on dismissal without reaching into the UI.
 *
 * An empty [categories] always renders as an error, whether the load actually failed or merely
 * hasn't happened yet — there is no separate flag for it. Defaulting [isLoading] to `true` keeps
 * that from flashing on the very first composition, before [categories] loads.
 *
 * @param progressSummary the User's per-Subcategory progress rollup (ADR-0016), backing the ring
 * and subtitle on every matched subcategory in [searchStatus]. `null` until [isProgressResolved] — and
 * possibly still `null` after, for a User who has never finished a session. Never read directly by
 * the screen; go through [progressFor]. Mirrors [com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreenState.progressSummary] — one
 * summary document serves both screens' rings.
 * @param isProgressResolved `false` until the summary read completes, success or failure alike — a
 * failed read leaves it `false` forever rather than surfacing an error, same as
 * [com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreenState.isProgressResolved]. Search results themselves never wait on this.
 */
data class BrowseScreenState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchStatus: SearchStatus = SearchStatus.Prompt,
    val progressSummary: ProgressSummary? = null,
    val isProgressResolved: Boolean = false,
) {

    /** One matched subcategory's ring/subtitle data — see [com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreenState.progressFor]. */
    fun progressFor(subcategoryId: String): SubcategoryProgress = progressSummary.subcategoryProgressFor(subcategoryId, isProgressResolved)
}
