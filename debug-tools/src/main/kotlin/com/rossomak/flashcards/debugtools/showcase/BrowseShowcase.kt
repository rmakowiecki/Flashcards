package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.domain.model.CategorySearchResults
import com.rossomak.flashcards.core.domain.model.CategoryWithSubcategorySummary
import com.rossomak.flashcards.core.domain.model.SubcategoryWithParentCategory
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.browse.BrowseContent
import com.rossomak.flashcards.feature.browse.BrowseScreenState
import com.rossomak.flashcards.feature.browse.BrowseSearchActions
import com.rossomak.flashcards.feature.browse.SearchStatus

private const val SEARCH_QUERY = "co"

/**
 * The Category list, with search results already in place: the expanded bar shows them for whatever
 * is typed, because the query reaches no ViewModel to change them.
 */
private val browseState = BrowseScreenState(
    isLoading = false,
    categories = ShowcaseTaxonomy.categories,
    progressSummary = ShowcaseTaxonomy.progressSummary,
    isProgressResolved = true,
    favorites = ShowcaseTaxonomy.favorites,
    searchStatus = SearchStatus.Results(searchResults()),
)

/**
 * What a search for [SEARCH_QUERY] would return: every Topic whose name starts with it, then their parent
 * Categories, each with the matched Topics first in its chip line.
 */
private fun searchResults(): CategorySearchResults = with(ShowcaseTaxonomy) {
    val matchedSubcategories = subcategoriesByCategoryId.values.flatten()
        .filter { it.name.startsWith(SEARCH_QUERY, ignoreCase = true) }
        .sortedBy { it.name }
    val matchedCategories = matchedSubcategories.map { it.categoryId }.distinct().map { categoryId ->
        val category = categoryById(categoryId)
        val matchedNames = matchedSubcategories.filter { it.categoryId == categoryId }.map { it.name }
        CategoryWithSubcategorySummary(category, (matchedNames + category.featuredSubcategoryNames).distinct())
    }
    CategorySearchResults(
        subcategories = matchedSubcategories.map { SubcategoryWithParentCategory(it, categoryById(it.categoryId)) },
        categories = matchedCategories,
    )
}

private val noOpSearchActions = BrowseSearchActions(onQueryChange = {}, onActivate = {}, onDismiss = {})

/** The Study tab. Tap the search bar and type to see the search results. */
@Composable
internal fun BrowseShowcase() {
    BrowseContent(
        modifier = Modifier.navigationBarsPadding(),
        state = browseState,
        onRefresh = {},
        searchActions = noOpSearchActions,
        onCategoryClick = { _, _ -> },
        onSubcategoryClick = {},
        onSubcategorySessionStart = {},
    )
}
