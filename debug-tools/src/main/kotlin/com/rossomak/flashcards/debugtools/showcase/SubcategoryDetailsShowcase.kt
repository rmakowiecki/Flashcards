package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.coroutines
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContent
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsDialog
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsScreenState

/** The Filter dialog's draft. */
private val FILTER_DRAFT_DIFFICULTY = 3..8
private const val FILTER_DRAFT_TAG_COUNT = 2

/** Unfiltered. The count is the Topic's real total; the list is a sample of it. */
private fun ShowcaseFixtures.subcategoryDetailsState(isFilterDialogOpen: Boolean): SubcategoryDetailsScreenState {
    val flashcards = flashcardsOf(coroutines.id)
    val tags = flashcards.flatMap { it.tags }.distinct().sorted()
    val filterDialog = SubcategoryDetailsDialog.Filters(
        draftState = FlashcardFilters(selectedTags = tags.take(FILTER_DRAFT_TAG_COUNT).toSet(), difficultyRange = FILTER_DRAFT_DIFFICULTY),
        availableTags = tags,
    )
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
        activeDialog = filterDialog.takeIf { isFilterDialogOpen },
    )
}

@Composable
internal fun SubcategoryDetailsShowcase(isFilterDialogOpen: Boolean, onNavigateBack: () -> Unit) {
    val fixtures = LocalShowcaseFixtures.current
    var state by remember(fixtures, isFilterDialogOpen) { mutableStateOf(fixtures.subcategoryDetailsState(isFilterDialogOpen)) }
    SubcategoryDetailsContent(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        onNavigateBack = onNavigateBack,
        onStartSession = {},
        onResetFilters = {},
        onFavoriteToggle = { state = state.copy(isFavorite = !state.isFavorite) },
        onAddShortcut = {},
        onRetry = {},
        onDialogEvent = { event -> state = state.copy(activeDialog = showcaseDialogAfter(event)) },
    )
}
