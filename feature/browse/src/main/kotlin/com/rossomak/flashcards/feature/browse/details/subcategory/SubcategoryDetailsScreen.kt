package com.rossomak.flashcards.feature.browse.details.subcategory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsBottomToolbar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.flashcardsListScrollFade
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupContainer
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupItems
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.browse.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

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
            is SubcategoryDetailsDestination.PreviewStudySession ->
                onNavigateToPreviewStudySession(
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

    val addedToFavorites = stringResource(R.string.favorites_added_message)
    val removedFromFavorites = stringResource(R.string.favorites_removed_message)
    val undoLabel = stringResource(R.string.favorites_undo_button)
    val shortcutPinUnsupported = stringResource(R.string.shortcut_pin_unsupported_message)

    // showSnackbar suspends until the snackbar is dismissed, and observeAsEvents hands over a plain
    // lambda, so the wait is launched rather than blocking the collector.
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        when (message) {
            SubcategoryDetailsMessage.AddedToFavorites, SubcategoryDetailsMessage.RemovedFromFavorites -> {
                val text = if (message == SubcategoryDetailsMessage.AddedToFavorites) addedToFavorites else removedFromFavorites
                snackbarScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = text,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onFavoriteUndo(restoreTo = message != SubcategoryDetailsMessage.AddedToFavorites)
                    }
                }
            }
            SubcategoryDetailsMessage.ShortcutPinUnsupported -> {
                snackbarScope.launch {
                    snackbarHostState.showSnackbar(message = shortcutPinUnsupported, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    SubcategoryDetailsContent(
        modifier = modifier,
        state = state,
        onNavigateBack = onNavigateBack,
        onStartSession = viewModel::onStartSession,
        onResetFilters = viewModel::onResetFilters,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onAddShortcut = viewModel::onAddShortcutClick,
        onDialogEvent = viewModel::onDialogEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
fun SubcategoryDetailsContent(
    modifier: Modifier = Modifier,
    state: SubcategoryDetailsScreenState,
    onNavigateBack: () -> Unit,
    onStartSession: () -> Unit,
    onResetFilters: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddShortcut: () -> Unit,
    onDialogEvent: (SubcategoryDetailsDialogEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Hoisted out of FlashcardList so it survives the Cards -> NoMatches -> Cards round trip, which
    // would otherwise drop the state and hide the reset below.
    val listState = rememberLazyListState()

    // Filtering or re-sorting yields a different list, so a retained offset would leave the user
    // mid-list on cards they never scrolled to. Both land them back at the top.
    //
    // Only the list resets: `scrollBehavior.state` is deliberately left alone, so a collapsed top
    // app bar stays collapsed rather than springing back open on every filter tweak.
    LaunchedEffect(state.filters, state.sortOrder) {
        listState.scrollToItem(index = 0)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubcategoryDetailsTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onFavoriteToggle = onFavoriteToggle,
                onAddShortcut = onAddShortcut,
            )
        },
        bottomBar = {
            SubcategoryDetailsBottomBar(
                state = state,
                onStartSession = onStartSession,
                onDialogEvent = onDialogEvent,
            )
        },
    ) { innerPadding ->
        when (val content = state.content) {
            SubcategoryDetailsContentState.Loading -> CenteredContent(modifier = Modifier.padding(innerPadding)) {
                CircularProgressIndicator()
            }

            is SubcategoryDetailsContentState.Error -> CenteredContent(modifier = Modifier.padding(innerPadding)) {
                Text(text = stringResource(content.messageRes))
            }

            is SubcategoryDetailsContentState.Cards -> FlashcardList(
                modifier = Modifier.padding(innerPadding),
                flashcards = content.flashcards,
                listState = listState,
            )
            // Resetting restores every tag and the difficulty range but deliberately leaves the sort order alone
            // sort cannot cause an empty result, so resetting it here would undo an unrelated choice (ADR-0022).
            SubcategoryDetailsContentState.NoMatches -> CenteredContent(modifier = Modifier.padding(innerPadding)) {
                FlashcardsEmptyState(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubcategoryDetailsTopBar(
    modifier: Modifier = Modifier,
    state: SubcategoryDetailsScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    Column(modifier = modifier) {
        FlashcardsTopAppBar(
            title = state.subcategoryName,
            subtitle = stringResource(R.string.subcategory_details_subtitle_label, state.categoryName),
            onNavigateBack = onNavigateBack,
            scrollBehavior = scrollBehavior,
            actions = {
                SubcategoryDetailsActions(
                    isFavorite = state.isFavorite,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddShortcut = onAddShortcut,
                )
            },
        )
        val cards = state.content as? SubcategoryDetailsContentState.Cards
        if (cards != null) {
            FlashcardsOverlineLabel(
                text = if (state.hasActiveFilters) {
                    pluralStringResource(
                        R.plurals.subcategory_details_filtered_card_count_label,
                        state.totalCount,
                        cards.flashcards.size,
                        state.totalCount,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.browse_card_count_label,
                        state.totalCount,
                        state.totalCount,
                    )
                },
            )
        }
    }
}

@Composable
private fun SubcategoryDetailsBottomBar(
    modifier: Modifier = Modifier,
    state: SubcategoryDetailsScreenState,
    onStartSession: () -> Unit,
    onDialogEvent: (SubcategoryDetailsDialogEvent) -> Unit,
) {
    FlashcardsBottomToolbar(
        modifier = modifier,
        actions = {
            SubcategoryDetailsToolbarActions(
                hasActiveFilters = state.hasActiveFilters,
                enabled = state.content is SubcategoryDetailsContentState.Cards ||
                    state.content is SubcategoryDetailsContentState.NoMatches,
                onFilterClick = {
                    onDialogEvent(
                        Open(SubcategoryDetailsDialog.Filters(state.filters, state.availableTags))
                    )
                },
                onSortClick = { onDialogEvent(Open(SubcategoryDetailsDialog.CardsSortingOrder(state.sortOrder))) },
            )
        },
        trailing = {
            FlashcardsFilledButton(
                // The count appears only once filters are on: unfiltered, it would just restate the
                // overline directly above it.
                text = if (state.hasActiveFilters) {
                    stringResource(
                        R.string.subcategory_details_start_session_with_count_button,
                        state.sessionCardCount,
                    )
                } else {
                    stringResource(CoreUiR.string.common_start_session_button)
                },
                onClick = onStartSession,
                size = FlashcardsComponentSize.Small,
                enabled = state.content is SubcategoryDetailsContentState.Cards,
                icon = Icons.Filled.PlayArrow,
            )
        },
    )
}

/**
 * Bookmark stays in the bar; anything past it falls into the overflow menu, which is how
 * [AppBarRow] renders `maxItemCount - 1` items inline.
 *
 * The bookmark is **deliberately cosmetic** — see
 * [SubcategoryDetailsViewModel.onFavoriteToggle].
 */
@Composable
private fun RowScope.SubcategoryDetailsActions(
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
private fun RowScope.SubcategoryDetailsToolbarActions(
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

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val expandedStateDescription = stringResource(R.string.subcategory_details_card_expanded_cd)
    val collapsedStateDescription = stringResource(R.string.subcategory_details_card_collapsed_cd)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.normal)
            .flashcardsListGroupContainer(listState)
            .flashcardsListScrollFade(listState),
    ) {
        flashcardsListGroupItems(
            items = flashcards.map { flashcard ->
                FlashcardsListGroupItem.ExpandableRow(
                    key = flashcard.id,
                    difficulty = flashcard.difficulty,
                    title = flashcard.question,
                    expanded = expandedStates[flashcard.id] ?: false,
                    onExpandedChange = { expanded -> expandedStates[flashcard.id] = expanded },
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
            },
        )
    }
}
