package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFlashcards
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContent
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsScreenState

private val coroutinesTags = ShowcaseFlashcards.coroutines.flatMap { it.tags }.distinct().sorted()

/** Every Coroutines card listed, every Tag selected and the full Difficulty range, as the screen opens. */
private val subcategoryDetailsState = SubcategoryDetailsScreenState(
    subcategoryId = ShowcaseTaxonomy.coroutines.id,
    categoryName = ShowcaseTaxonomy.coroutines.categoryName,
    subcategoryName = ShowcaseTaxonomy.coroutines.name,
    content = SubcategoryDetailsContentState.FlashcardsList(ShowcaseFlashcards.coroutines),
    sortOrder = FlashcardSortOrder.Default,
    filters = FlashcardFilters(selectedTags = coroutinesTags.toSet(), difficultyRange = SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS),
    availableTags = coroutinesTags,
    totalCount = ShowcaseFlashcards.coroutines.size,
    isFavorite = true,
)

@Composable
internal fun SubcategoryDetailsShowcase(onNavigateBack: () -> Unit) {
    SubcategoryDetailsContent(
        state = subcategoryDetailsState,
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
