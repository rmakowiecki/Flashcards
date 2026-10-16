package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private fun ShowcaseFixtures.categoryDetailsState(isSelectionMode: Boolean) = CategoryDetailsScreenState(
    categoryId = kotlin.id,
    categoryName = kotlin.name,
    content = CategoryDetailsContentState.SubcategoriesList(subcategoriesOf(kotlin.id)),
    progressSummary = showcaseProgressSummary,
    isProgressResolved = true,
    favorites = showcaseFavorites,
    selectedSubcategoryIds = if (isSelectionMode) setOf(coroutines.id, functions.id) else null,
)

/** Leaving Selection Mode closes the showcase too: its back handler would otherwise swallow back. */
@Composable
internal fun CategoryDetailsShowcase(isSelectionMode: Boolean, onNavigateBack: () -> Unit) {
    val fixtures = LocalShowcaseFixtures.current
    CategoryDetailsContent(
        state = remember(fixtures, isSelectionMode) { fixtures.categoryDetailsState(isSelectionMode) },
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
