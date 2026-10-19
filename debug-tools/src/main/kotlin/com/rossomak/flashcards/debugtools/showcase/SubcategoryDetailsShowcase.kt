package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.coroutines
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContent
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsScreenState

/** Unfiltered, as the screen opens. The count is the Topic's real total; the list is a sample of it. */
private fun ShowcaseFixtures.subcategoryDetailsState(): SubcategoryDetailsScreenState {
    val flashcards = flashcardsOf(coroutines.id)
    val tags = flashcards.flatMap { it.tags }.distinct().sorted()
    return SubcategoryDetailsScreenState(
        subcategoryId = coroutines.id,
        categoryName = coroutines.categoryName,
        subcategoryName = coroutines.name,
        content = SubcategoryDetailsContentState.FlashcardsList(flashcards),
        sortOrder = FlashcardSortOrder.Default,
        filters = FlashcardFilters(selectedTags = tags.toSet(), difficultyRange = SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS),
        availableTags = tags,
        totalCount = coroutines.cardCount,
        isFavorite = true,
    )
}

@Composable
internal fun SubcategoryDetailsShowcase(onNavigateBack: () -> Unit) {
    val fixtures = LocalShowcaseFixtures.current
    SubcategoryDetailsContent(
        state = remember(fixtures) { fixtures.subcategoryDetailsState() },
        snackbarHostState = remember { SnackbarHostState() },
        onNavigateBack = onNavigateBack,
        onStartSession = {},
        onResetFilters = {},
        onFavoriteToggle = {},
        onAddShortcut = {},
        onRetry = {},
        onDialogEvent = {},
    )
}
