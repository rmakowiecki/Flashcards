package com.rossomak.flashcards.feature.browse.details.subcategory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsBottomToolbar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.composables.flashcardsListScrollFade
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsExpandableListRow
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupContainer
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupItems
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.browse.R
import com.rossomak.flashcards.feature.browse.details.DetailsMessagesEffect
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState.Error
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState.FlashcardsList
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState.Loading
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState.NoMatches
import kotlinx.collections.immutable.toImmutableList

@Composable
fun SubcategoryDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: SubcategoryDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPreviewStudySession: (
        categoryId: String,
        categoryName: String,
        subcategoryId: String,
        subcategoryName: String,
        filterTagIds: List<String>,
        difficultyRange: IntRange,
        sortOrder: FlashcardSortOrder,
    ) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is SubcategoryDetailsDestination.PreviewStudySession -> onNavigateToPreviewStudySession(
                destination.categoryId,
                destination.categoryName,
                destination.subcategoryId,
                destination.subcategoryName,
                destination.filterTagIds,
                destination.difficultyRange,
                destination.sortOrder,
            )
        }
    }
    DetailsMessagesEffect(
        messages = viewModel.messages,
        snackbarHostState = snackbarHostState,
        onFavoriteUndo = viewModel::onFavoriteUndo,
    )
    SubcategoryDetailsContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onStartSession = viewModel::onStartSession,
        onResetFilters = viewModel::onResetFilters,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onAddShortcut = viewModel::onAddShortcut,
        onRetry = viewModel::onRetry,
        onDialogEvent = viewModel::onDialogEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
fun SubcategoryDetailsContent(
    modifier: Modifier = Modifier,
    state: SubcategoryDetailsScreenState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onStartSession: () -> Unit,
    onResetFilters: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddShortcut: () -> Unit,
    onRetry: () -> Unit,
    onDialogEvent: (SubcategoryDetailsDialogEvent) -> Unit,
) {
    // Hoisted out of FlashcardList so it survives the Cards -> NoMatches -> Cards round trip, which
    // would otherwise drop the state and hide the reset below.
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Filtering or re-sorting yields a different list, so a retained offset would leave the user
    // mid-list on cards they never scrolled to. Both land them back at the top.
    // Only the list resets: `scrollBehavior.state` is deliberately left alone, so a collapsed top
    // app bar stays collapsed rather than springing back open on every filter tweak.
    LaunchedEffect(state.filters, state.sortOrder) {
        listState.scrollToItem(index = 0)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The bars take only the fields they render, so a dialog draft tick (the dialog lives in
        // screen state, ADR-0036) leaves them skipped.
        topBar = {
            SubcategoryDetailsTopBar(
                subcategoryName = state.subcategoryName,
                categoryName = state.categoryName,
                isFavorite = state.isFavorite,
                visibleCardCount = (state.content as? FlashcardsList)?.flashcards?.size,
                totalCount = state.totalCount,
                hasActiveFilters = state.hasActiveFilters,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onFavoriteToggle = onFavoriteToggle,
                onAddShortcut = onAddShortcut,
            )
        },
        bottomBar = {
            SubcategoryDetailsBottomBar(
                hasActiveFilters = state.hasActiveFilters,
                sessionCardCount = state.sessionCardCount,
                canStartSession = state.content is FlashcardsList,
                areControlsEnabled = state.content is FlashcardsList || state.content is NoMatches,
                filters = state.filters,
                availableTags = state.availableTags,
                sortOrder = state.sortOrder,
                onStartSession = onStartSession,
                onDialogEvent = onDialogEvent,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val content = state.content) {
                Loading -> CircularProgressIndicator()
                is FlashcardsList -> FlashcardList(
                    modifier = Modifier.align(Alignment.TopCenter),
                    flashcards = content.flashcards,
                    listState = listState,
                )

                is Error -> FlashcardsEmptyState(
                    icon = Icons.Filled.ErrorOutline,
                    title = stringResource(CoreUiR.string.common_load_error_title),
                    supportingText = stringResource(content.messageRes),
                    tone = FlashcardsEmptyStateTone.Error,
                    button = {
                        FlashcardsFilledButton(
                            text = stringResource(CoreUiR.string.common_retry_button),
                            onClick = onRetry,
                        )
                    },
                )
                // Resetting restores every tag and the difficulty range but deliberately leaves the sort order alone
                // sort cannot cause an empty result, so resetting it here would undo an unrelated choice (ADR-0022).
                NoMatches -> FlashcardsEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.subcategory_details_no_matches_title),
                    supportingText = stringResource(R.string.subcategory_details_no_matches_message),
                    button = {
                        FlashcardsFilledButton(
                            text = stringResource(R.string.subcategory_details_reset_filters_button),
                            onClick = onResetFilters,
                            icon = Icons.Filled.Close,
                        )
                    },
                )
            }
        }
    }

    SubcategoryDetailsDialogHost(activeDialog = state.activeDialog, onDialogEvent = onDialogEvent)
}

/** [visibleCardCount] is `null` unless cards are listed, which hides the overline. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // one field per rendered value; the whole state would recompose on every dialog draft tick.
@Composable
private fun SubcategoryDetailsTopBar(
    modifier: Modifier = Modifier,
    subcategoryName: String,
    categoryName: String,
    isFavorite: Boolean,
    visibleCardCount: Int?,
    totalCount: Int,
    hasActiveFilters: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    Column(modifier = modifier) {
        FlashcardsTopAppBar(
            title = subcategoryName,
            subtitle = stringResource(R.string.subcategory_details_subtitle_label, categoryName),
            onNavigateBack = onNavigateBack,
            scrollBehavior = scrollBehavior,
            actions = {
                SubcategoryDetailsActions(
                    isFavorite = isFavorite,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddShortcut = onAddShortcut,
                )
            },
        )
        if (visibleCardCount != null) {
            FlashcardsOverlineLabel(
                text = if (hasActiveFilters) {
                    pluralStringResource(
                        R.plurals.subcategory_details_filtered_card_count_label,
                        totalCount,
                        visibleCardCount,
                        totalCount,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.browse_card_count_label,
                        totalCount,
                        totalCount,
                    )
                },
            )
        }
    }
}

@Suppress("LongParameterList") // one field per rendered value; the whole state would recompose on every dialog draft tick.
@Composable
private fun SubcategoryDetailsBottomBar(
    modifier: Modifier = Modifier,
    hasActiveFilters: Boolean,
    sessionCardCount: Int,
    canStartSession: Boolean,
    areControlsEnabled: Boolean,
    filters: FlashcardFilters,
    availableTags: List<String>,
    sortOrder: FlashcardSortOrder,
    onStartSession: () -> Unit,
    onDialogEvent: (SubcategoryDetailsDialogEvent) -> Unit,
) {
    FlashcardsBottomToolbar(
        modifier = modifier,
        actions = {
            SubcategoryDetailsToolbarActions(
                hasActiveFilters = hasActiveFilters,
                enabled = areControlsEnabled,
                onFilterClick = { onDialogEvent(Open(SubcategoryDetailsDialog.Filters(filters, availableTags))) },
                onSortClick = { onDialogEvent(Open(SubcategoryDetailsDialog.CardsSortingOrder(sortOrder))) },
            )
        },
        trailing = {
            FlashcardsFilledButton(
                // The count appears only once filters are on: unfiltered, it would just restate the
                // overline directly above it.
                text = if (hasActiveFilters) {
                    stringResource(
                        R.string.subcategory_details_start_session_with_count_button,
                        sessionCardCount,
                    )
                } else {
                    stringResource(CoreUiR.string.common_start_session_button)
                },
                onClick = onStartSession,
                size = FlashcardsComponentSize.Small,
                enabled = canStartSession,
                icon = Icons.Filled.PlayArrow,
            )
        },
    )
}

@Composable
private fun SubcategoryDetailsActions(
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    val bookmarkLabel = stringResource(R.string.subcategory_details_bookmark_label)
    val addShortcutLabel = stringResource(R.string.subcategory_details_add_shortcut_label)

    AppBarRow(
        overflowIndicator = { menuState ->
            IconButton(onClick = { menuState.show() }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.subcategory_details_more_options_cd),
                )
            }
        },
        maxItemCount = 2,
    ) {
        clickableItem(
            onClick = onFavoriteToggle,
            icon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = null,
                )
            },
            label = bookmarkLabel,
        )
        clickableItem(
            onClick = onAddShortcut,
            icon = { Icon(imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
            label = addShortcutLabel,
        )
    }
}

/**
 * Filter and sort, in the order ADR-0022 fixes them. Add-card is deliberately absent until a
 * Private flashcard creation flow exists — it comes back with that work.
 *
 * Sort carries **no badge**, unlike Filter: it is seeded from the user's saved preference, so a
 * "non-default" dot would be permanently lit for anyone whose saved order is not Default — a dot
 * they could never clear from this screen (ADR-0038).
 */
@Composable
private fun SubcategoryDetailsToolbarActions(
    hasActiveFilters: Boolean,
    enabled: Boolean,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    BadgedBox(badge = { if (hasActiveFilters) Badge() }) {
        IconButton(onClick = onFilterClick, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = stringResource(R.string.subcategory_details_filter_cd),
            )
        }
    }
    IconButton(onClick = onSortClick, enabled = enabled) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.subcategory_details_sort_cd),
        )
    }
}

/**
 * A subcategory's flashcards can run into the dozens, so this binds `flashcardsListGroupItems`
 * inside a `LazyColumn` rather than the bounded `FlashcardsListGroup` — only rows near the
 * viewport get composed.
 */
@Composable
private fun FlashcardList(
    modifier: Modifier = Modifier,
    flashcards: List<Flashcard>,
    listState: LazyListState,
) {
    // Saveable, so expanded cards stay expanded across a trip to Preview and back, rotation and
    // process death. Still UI state: the ViewModel never reads it.
    var expandedFlashcardIds by rememberSaveable(stateSaver = ExpandedFlashcardIdsSaver) { mutableStateOf(emptySet<String>()) }
    val expandedStateDescription = stringResource(R.string.subcategory_details_card_expanded_cd)
    val collapsedStateDescription = stringResource(R.string.subcategory_details_card_collapsed_cd)

    val onFlashcardExpandedChange = { flashcardId: String, expanded: Boolean ->
        expandedFlashcardIds = if (expanded) expandedFlashcardIds + flashcardId else expandedFlashcardIds - flashcardId
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.normal)
            .flashcardsListGroupContainer(listState)
            .flashcardsListScrollFade(listState),
    ) {
        flashcardsListGroupItems(
            items = flashcards,
            key = { flashcard -> flashcard.id },
        ) { flashcard, rowModifier ->
            FlashcardRow(
                modifier = rowModifier,
                flashcard = flashcard,
                expanded = flashcard.id in expandedFlashcardIds,
                expandedStateDescription = expandedStateDescription,
                collapsedStateDescription = collapsedStateDescription,
                onExpandedChange = onFlashcardExpandedChange,
            )
        }
    }
}

/**
 * One card, built inside its own lazy item scope with only per-row values, so expanding one card
 * recomposes that card alone.
 */
@Composable
private fun FlashcardRow(
    modifier: Modifier,
    flashcard: Flashcard,
    expanded: Boolean,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    onExpandedChange: (flashcardId: String, expanded: Boolean) -> Unit,
) {
    FlashcardsExpandableListRow(
        modifier = modifier,
        difficulty = flashcard.difficulty,
        title = flashcard.question,
        expanded = expanded,
        onExpandedChange = { isExpanded -> onExpandedChange(flashcard.id, isExpanded) },
        expandedStateDescription = expandedStateDescription,
        collapsedStateDescription = collapsedStateDescription,
        tags = flashcard.tags.toImmutableList(),
        expandedContent = {
            Text(
                text = flashcard.answer.withInlineCode(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

/** A Bundle can't hold a `Set`, so the expanded ids round-trip through a list. */
private val ExpandedFlashcardIdsSaver = listSaver<Set<String>, String>(
    save = { expandedIds -> expandedIds.toList() },
    restore = { savedIds -> savedIds.toSet() },
)
