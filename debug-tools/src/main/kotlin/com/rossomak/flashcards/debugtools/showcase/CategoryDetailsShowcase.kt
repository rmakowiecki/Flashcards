package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsContent
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsScreenState

private val categoryDetailsState = with(ShowcaseTaxonomy) {
    CategoryDetailsScreenState(
        categoryId = kotlin.id,
        categoryName = kotlin.name,
        content = CategoryDetailsContentState.SubcategoriesList(subcategoriesOf(kotlin)),
        progressSummary = progressSummary,
        isProgressResolved = true,
        favorites = favorites,
    )
}

private val selectionModeState = categoryDetailsState.copy(
    selectedSubcategoryIds = setOf(ShowcaseTaxonomy.coroutines.id, ShowcaseTaxonomy.flows.id),
)

/**
 * Kotlin's Topics, or the same list in Selection Mode with two Topics picked. Back and leaving
 * Selection Mode both close the showcase: Selection Mode's back handler would otherwise swallow back.
 */
@Composable
internal fun CategoryDetailsShowcase(isSelectionMode: Boolean, onNavigateBack: () -> Unit) {
    CategoryDetailsContent(
        state = if (isSelectionMode) selectionModeState else categoryDetailsState,
        onNavigateBack = onNavigateBack,
        onSubcategorySelect = {},
        onSubcategorySessionStart = {},
        onSelectionModeToggle = onNavigateBack,
        onSubcategoryLongPress = {},
        onSubcategorySelectionChange = { _, _ -> },
        onSelectAllToggle = {},
        onQuickSessionStart = {},
        onCustomSessionStart = {},
        onFavoriteToggle = {},
        onAddShortcut = {},
        onRetry = {},
    )
}
