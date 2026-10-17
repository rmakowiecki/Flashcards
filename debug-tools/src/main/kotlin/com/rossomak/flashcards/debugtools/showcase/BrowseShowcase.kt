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

private const val SEARCH_QUERY = "con"

// Left out of the search results so the showcase reads cleanly: Apple Platforms entirely, and every Concurrency but Swift's.
private val hiddenSearchCategoryIds = setOf("apple-platforms")
private val hiddenSearchSubcategoryIds = setOf("android-concurrency", "python-concurrency")

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
 * What a search for [SEARCH_QUERY] returns, less the hidden Categories and Topics: every Topic whose name starts
 * with it, alphabetically, then their parent Categories in stored order, each chip line led by its matched Topics,
 * most cards first.
 */
private fun searchResults(): CategorySearchResults = with(ShowcaseTaxonomy) {
    val matchedSubcategories = subcategoriesByCategoryId.values.flatten()
        .filter { it.name.startsWith(SEARCH_QUERY, ignoreCase = true) }
        .filterNot { it.categoryId in hiddenSearchCategoryIds || it.id in hiddenSearchSubcategoryIds }
        .sortedBy { it.name }
    val matchedCategories = matchedSubcategories.map { categoryById(it.categoryId) }.distinct().sortedBy { it.order }.map { category ->
        val matchedNames = matchedSubcategories.filter { it.categoryId == category.id }.sortedByDescending { it.cardCount }.map { it.name }
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
