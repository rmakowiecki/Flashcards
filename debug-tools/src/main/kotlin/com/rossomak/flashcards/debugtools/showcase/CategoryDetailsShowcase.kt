package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.coroutines
import com.rossomak.flashcards.debugtools.showcase.fixtures.functions
import com.rossomak.flashcards.debugtools.showcase.fixtures.kotlin
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseFavorites
import com.rossomak.flashcards.debugtools.showcase.fixtures.showcaseProgressSummary
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsContent
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreenState
import java.time.Instant

private fun ShowcaseFixtures.categoryDetailsState(isSelectionMode: Boolean) = CategoryDetailsScreenState(
    categoryId = kotlin.id,
    categoryName = kotlin.name,
    content = CategoryDetailsContentState.SubcategoriesList(subcategoriesOf(kotlin.id)),
    progressSummary = showcaseProgressSummary,
    isProgressResolved = true,
    // Selection Mode shows the bookmark unfilled.
    favorites = if (isSelectionMode) showcaseFavorites.copy(categoryIds = showcaseFavorites.categoryIds - kotlin.id) else showcaseFavorites,
    selectedSubcategoryIds = if (isSelectionMode) setOf(coroutines.id, functions.id) else null,
)

private fun CategoryDetailsScreenState.withSelectionModeToggled() = copy(selectedSubcategoryIds = if (isSelectionMode) null else emptySet())

private fun CategoryDetailsScreenState.withSelection(subcategoryId: String, isSelected: Boolean): CategoryDetailsScreenState {
    val selectedIds = selectedSubcategoryIds ?: return this
    return copy(selectedSubcategoryIds = if (isSelected) selectedIds + subcategoryId else selectedIds - subcategoryId)
}

private fun CategoryDetailsScreenState.withAllSelectedToggled(): CategoryDetailsScreenState {
    val subcategories = (content as? CategoryDetailsContentState.SubcategoriesList)?.subcategories ?: return this
    return copy(selectedSubcategoryIds = if (isAllSelected) emptySet() else subcategories.map { it.id }.toSet())
}

private fun CategoryDetailsScreenState.withFavoriteToggled(): CategoryDetailsScreenState {
    val categoryIds = if (isFavorite) favorites.categoryIds - categoryId else favorites.categoryIds + (categoryId to Instant.now())
    return copy(favorites = favorites.copy(categoryIds = categoryIds))
}

@Composable
internal fun CategoryDetailsShowcase(isSelectionMode: Boolean, onNavigateBack: () -> Unit) {
    val fixtures = LocalShowcaseFixtures.current
    var state by remember(fixtures, isSelectionMode) { mutableStateOf(fixtures.categoryDetailsState(isSelectionMode)) }
    CategoryDetailsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSubcategorySelect = {},
        onSubcategorySessionStart = {},
        onSelectionModeToggle = { state = state.withSelectionModeToggled() },
        onSubcategoryLongPress = { subcategoryId -> state = state.copy(selectedSubcategoryIds = setOf(subcategoryId)) },
        onSubcategorySelectionChange = { subcategoryId, isSelected -> state = state.withSelection(subcategoryId, isSelected) },
        onSelectAllToggle = { state = state.withAllSelectedToggled() },
        onQuickSessionStart = {},
        onCustomSessionStart = {},
        onFavoriteToggle = { state = state.withFavoriteToggled() },
        onAddShortcut = {},
        onRetry = {},
    )
}
