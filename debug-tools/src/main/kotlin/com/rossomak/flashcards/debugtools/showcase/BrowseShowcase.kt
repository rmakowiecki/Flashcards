package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.domain.model.CategorySearchResults
import com.rossomak.flashcards.core.domain.model.CategoryWithSubcategorySummary
import com.rossomak.flashcards.core.domain.model.SubcategoryWithParentCategory
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseFavorites
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseProgressSummary
import com.rossomak.flashcards.feature.browse.BrowseContent
import com.rossomak.flashcards.feature.browse.BrowseScreenState
import com.rossomak.flashcards.feature.browse.BrowseSearchActions
import com.rossomak.flashcards.feature.browse.SearchStatus

private const val SEARCH_QUERY = "con"

// Hidden from the results so they read cleanly.
private val hiddenSearchCategoryIds = setOf("apple-platforms")
private val hiddenSearchSubcategoryIds = setOf("android-concurrency", "python-concurrency")

private val noOpSearchActions = BrowseSearchActions(onQueryChange = {}, onActivate = {}, onDismiss = {})

/** The results always show, whatever is typed: the query reaches no ViewModel. */
private fun ShowcaseFixtures.browseState() = BrowseScreenState(
    isLoading = false,
    categories = categories,
    progressSummary = showcaseProgressSummary,
    isProgressResolved = true,
    favorites = showcaseFavorites,
    searchStatus = SearchStatus.Results(searchResults()),
)

/** [SEARCH_QUERY]'s results, ordered and chip-lined as the real search does it. */
private fun ShowcaseFixtures.searchResults(): CategorySearchResults {
    val matchedSubcategories = subcategories
        .filter { it.name.startsWith(SEARCH_QUERY, ignoreCase = true) }
        .filterNot { it.categoryId in hiddenSearchCategoryIds || it.id in hiddenSearchSubcategoryIds }
        .sortedBy { it.name }
    val matchedCategories = matchedSubcategories.map { category(it.categoryId) }.distinct().sortedBy { it.order }.map { category ->
        val matchedNames = matchedSubcategories.filter { it.categoryId == category.id }.sortedByDescending { it.cardCount }.map { it.name }
        CategoryWithSubcategorySummary(category, (matchedNames + category.featuredSubcategoryNames).distinct())
    }
    return CategorySearchResults(
        subcategories = matchedSubcategories.map { SubcategoryWithParentCategory(it, category(it.categoryId)) },
        categories = matchedCategories,
    )
}

@Composable
internal fun BrowseShowcase() {
    val fixtures = LocalShowcaseFixtures.current
    BrowseContent(
        modifier = Modifier.navigationBarsPadding(),
        state = remember(fixtures) { fixtures.browseState() },
        onRefresh = {},
        searchActions = noOpSearchActions,
        onCategoryClick = { _, _ -> },
        onSubcategoryClick = {},
        onSubcategorySessionStart = {},
    )
}
