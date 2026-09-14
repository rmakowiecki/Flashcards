package com.rossomak.flashcards.feature.browse.details.category

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AppBarRow
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.FlashcardsProgressRing
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsBottomToolbar
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsTopAppBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.flashcardsListScrollFade
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupContainer
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupItems
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.browse.R
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress.Resolved
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress.Unresolved
import kotlinx.coroutines.launch

@Composable
fun CategoryDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (categoryId: String, categoryName: String, subcategoryId: String, subcategoryName: String) -> Unit,
    onNavigateToPreviewStudySessionForCategory: (
        categoryId: String,
        categoryName: String,
        subcategoryIds: List<String>,
        subcategoryNames: List<String>,
        isQuickSession: Boolean,
    ) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Only the two session CTAs go through the ViewModel's event channel — they're the only navigations that aggregate state across rows.
    // The row's own tap and its play button stay inline lambdas below: each already holds the Subcategory it needs.
    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is CategoryDetailsDestination.PreviewStudySession ->
                onNavigateToPreviewStudySessionForCategory(
                    destination.categoryId,
                    destination.categoryName,
                    destination.subcategoryIds,
                    destination.subcategoryNames,
                    destination.isQuickSession,
                )
        }
    }

    val addedToFavorites = stringResource(R.string.favorites_added_message)
    val removedFromFavorites = stringResource(R.string.favorites_removed_message)
    val undoLabel = stringResource(R.string.favorites_undo_button)

    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            CategoryDetailsMessage.AddedToFavorites -> addedToFavorites
            CategoryDetailsMessage.RemovedFromFavorites -> removedFromFavorites
        }
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onFavoriteUndo(restoreTo = message != CategoryDetailsMessage.AddedToFavorites)
            }
        }
    }

    CategoryDetailsContent(
        modifier = modifier,
        state = state,
        onNavigateBack = onNavigateBack,
        onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
        onNavigateToPreviewStudySession = { subcategory ->
            onNavigateToPreviewStudySession(
                subcategory.categoryId,
                subcategory.categoryName,
                subcategory.id,
                subcategory.name,
            )
        },
        onSelectionModeToggle = viewModel::onSelectionModeToggle,
        onSubcategoryLongPress = viewModel::onSubcategoryLongPress,
        onSubcategorySelectionChange = viewModel::onSubcategorySelectionChange,
        onSelectAllToggle = viewModel::onSelectAllToggle,
        onQuickSessionStart = viewModel::onQuickSessionStart,
        onCustomSessionStart = viewModel::onCustomSessionStart,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
fun CategoryDetailsContent(
    modifier: Modifier = Modifier,
    state: CategoryDetailsScreenState,
    onNavigateBack: () -> Unit,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (Subcategory) -> Unit,
    onSelectionModeToggle: () -> Unit,
    onSubcategoryLongPress: (String) -> Unit,
    onSubcategorySelectionChange: (String, Boolean) -> Unit,
    onSelectAllToggle: () -> Unit,
    onQuickSessionStart: () -> Unit,
    onCustomSessionStart: () -> Unit,
    onFavoriteToggle: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Hoisted here, and never reset: the row set is identical in both modes and only the chrome changes, unlike Subcategory Details where filtering changes which items exist.
    // Resetting on list mode switch would throw a user who long-pressed halfway down the list back to the top.
    val listState = rememberLazyListState()

    // System back leaves Selection Mode (if active) rather than the screen, same as the top app bar's back arrow below
    BackHandler(enabled = state.isSelectionMode, onBack = onSelectionModeToggle)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                FlashcardsTopAppBar(
                    title = state.categoryName,
                    subtitle = categoryDetailsSubtitle(state),
                    onNavigateBack = if (state.isSelectionMode) onSelectionModeToggle else onNavigateBack,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        CategoryDetailsActions(
                            isFavorite = state.isFavorite,
                            onFavoriteToggle = onFavoriteToggle,
                        )
                    },
                )
                if (!state.isLoading && state.errorResId == null) {
                    FlashcardsOverlineLabel(text = categoryDetailsOverline(state))
                }
            }
        },
        bottomBar = {
            CategoryDetailsBottomBar(
                state = state,
                onSelectionModeToggle = onSelectionModeToggle,
                onSelectAllToggle = onSelectAllToggle,
                onQuickSessionStart = onQuickSessionStart,
                onCustomSessionStart = onCustomSessionStart,
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.errorResId != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(state.errorResId))
            }

            else -> SubcategoryList(
                modifier = Modifier.padding(innerPadding),
                listState = listState,
                subcategories = state.subcategories,
                isSelectionMode = state.isSelectionMode,
                selectedSubcategoryIds = state.selectedSubcategoryIds ?: emptySet(),
                progressFor = state::progressFor,
                onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                onNavigateToPreviewStudySession = onNavigateToPreviewStudySession,
                onSubcategoryLongPress = onSubcategoryLongPress,
                onSubcategorySelectionChange = onSubcategorySelectionChange,
            )
        }
    }
}

/**
 * "Category" by default; in Selection Mode, the selection's total card count — so the size of a
 * Custom session is legible before starting it — falling back to a bare label rather than
 * rendering "· 0 cards" while nothing is selected yet.
 */
@Composable
private fun categoryDetailsSubtitle(state: CategoryDetailsScreenState): String =
    if (state.isSelectionMode) {
        if (state.selectedCount > 0) {
            pluralStringResource(
                R.plurals.category_details_selection_subtitle_with_count_label,
                state.selectedCardCount,
                state.selectedCardCount,
            )
        } else {
            stringResource(R.string.category_details_selection_subtitle_label)
        }
    } else {
        stringResource(R.string.category_details_subtitle_label)
    }

@Composable
private fun categoryDetailsOverline(state: CategoryDetailsScreenState): String =
    if (state.isSelectionMode) {
        pluralStringResource(
            R.plurals.category_details_selection_overline_label,
            state.subcategories.size,
            state.selectedCount,
            state.subcategories.size,
        )
    } else {
        pluralStringResource(
            R.plurals.browse_category_topic_count,
            state.subcategories.size,
            state.subcategories.size,
        )
    }

/**
 * Default list mode carries one control — the Selection Mode toggle — beside the **Quick session** CTA.
 * Selection Mode swaps it for exit plus select-all/deselect-all, beside **Custom session**. The bar
 * itself is always rendered (it holds the screen's primary action, per
 * [FlashcardsBottomToolbar]'s own contract) — availability is expressed through `enabled` instead,
 * so Selection Mode is simply unreachable while loading, errored, or on an empty Category.
 */
@Composable
private fun CategoryDetailsBottomBar(
    modifier: Modifier = Modifier,
    state: CategoryDetailsScreenState,
    onSelectionModeToggle: () -> Unit,
    onSelectAllToggle: () -> Unit,
    onQuickSessionStart: () -> Unit,
    onCustomSessionStart: () -> Unit,
) {
    val hasSubcategories = state.subcategories.isNotEmpty()
    FlashcardsBottomToolbar(
        modifier = modifier,
        actions = {
            if (state.isSelectionMode) {
                SelectionModeToolbarActions(
                    isAllSelected = state.isAllSelected,
                    onSelectionModeToggle = onSelectionModeToggle,
                    onSelectAllToggle = onSelectAllToggle,
                )
            } else {
                IconButton(onClick = onSelectionModeToggle, enabled = hasSubcategories) {
                    Icon(
                        imageVector = Icons.Filled.Checklist,
                        contentDescription = stringResource(R.string.category_details_selection_mode_enter_cd),
                    )
                }
            }
        },
        trailing = {
            if (state.isSelectionMode) {
                FlashcardsFilledButton(
                    text = stringResource(R.string.category_details_custom_session_button, state.selectedCount),
                    onClick = onCustomSessionStart,
                    size = FlashcardsComponentSize.Small,
                    enabled = state.selectedCount > 0,
                    icon = Icons.Filled.PlayArrow,
                )
            } else {
                FlashcardsFilledButton(
                    text = stringResource(R.string.category_details_quick_session_button),
                    onClick = onQuickSessionStart,
                    size = FlashcardsComponentSize.Small,
                    enabled = hasSubcategories,
                    icon = Icons.Filled.Bolt,
                )
            }
        },
    )
}

/**
 * Exit sits where the mode toggle sat, so the control that changes meaning does not also change
 * position. Select-all's icon and content description both swap on [isAllSelected], since it is
 * one control with two meanings rather than two controls.
 */
@Composable
private fun SelectionModeToolbarActions(
    isAllSelected: Boolean,
    onSelectionModeToggle: () -> Unit,
    onSelectAllToggle: () -> Unit,
) {
    IconButton(onClick = onSelectionModeToggle) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.category_details_selection_mode_exit_cd),
        )
    }
    IconButton(onClick = onSelectAllToggle) {
        Icon(
            imageVector = if (isAllSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
            contentDescription = stringResource(
                if (isAllSelected) R.string.category_details_deselect_all_cd else R.string.category_details_select_all_cd,
            ),
        )
    }
}

/**
 * Bookmark stays in the bar; anything past it falls into the overflow menu, which is how
 * [AppBarRow] renders `maxItemCount - 1` items inline.
 *
 * The bookmark is **deliberately cosmetic** — see [CategoryDetailsViewModel.onFavoriteToggle].
 * Add-to-home-screen is still unwired, pending the dynamic launcher shortcut work.
 */
@Composable
private fun CategoryDetailsActions(
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
) {
    val bookmarkLabel = stringResource(R.string.category_details_bookmark_label)
    val addShortcutLabel = stringResource(R.string.category_details_add_shortcut_label)

    AppBarRow(
        overflowIndicator = { menuState ->
            IconButton(onClick = { menuState.show() }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.category_details_more_options_cd),
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
            onClick = {},
            icon = { Icon(imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
            label = addShortcutLabel,
        )
    }
}

@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
@Composable
private fun SubcategoryList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    subcategories: List<Subcategory>,
    isSelectionMode: Boolean,
    selectedSubcategoryIds: Set<String>,
    progressFor: (String) -> SubcategoryProgress,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (Subcategory) -> Unit,
    onSubcategoryLongPress: (String) -> Unit,
    onSubcategorySelectionChange: (String, Boolean) -> Unit,
) {
    val resources = LocalResources.current
    val rowSubtitleSeparator = resources.getString(R.string.browse_middle_dot_separator)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.normal)
            .flashcardsListGroupContainer(listState)
            .flashcardsListScrollFade(listState)
    ) {
        flashcardsListGroupItems(
            items = subcategories.map { subcategory ->
                val progress = progressFor(subcategory.id)
                subcategory.toListGroupItem(
                    isSelectionMode = isSelectionMode,
                    isSelected = subcategory.id in selectedSubcategoryIds,
                    progress = progress,
                    playContentDescription = resources.getString(R.string.category_details_topic_play_cd, subcategory.name),
                    ringContentDescription = progress.ringContentDescription(resources, subcategory.cardCount),
                    cardCountLabel = resources.getQuantityString(
                        R.plurals.browse_card_count_label,
                        subcategory.cardCount,
                        subcategory.cardCount,
                    ),
                    studiedText = progress.studiedLabel(resources),
                    rowSubtitleSeparator = rowSubtitleSeparator,
                    onNavigateToSubcategoryDetails = onNavigateToSubcategoryDetails,
                    onNavigateToPreviewStudySession = onNavigateToPreviewStudySession,
                    onLongPress = onSubcategoryLongPress,
                    onSelectedChange = onSubcategorySelectionChange,
                )
            }
        )
    }
}

private const val PROGRESS_PERCENT_SCALE = 100

/**
 * `"12 studied"` once resolved with a nonzero count; `null` while [Unresolved] or
 * once resolved to zero — either way there's nothing studied to report yet, so
 * [CategoryDetailsRowSubtitle] drops the segment rather than showing "— studied" or "0 studied".
 */
private fun SubcategoryProgress.studiedLabel(resources: Resources): String? =
    when (this) {
        Unresolved -> null
        is Resolved -> if (studiedCount > 0) resources.getString(R.string.category_details_topic_studied_label, studiedCount) else null
    }

/**
 * Names Studied, never a number in the unknown state. [cardCount] is the ring's denominator — kept
 * separate from [SubcategoryProgress] itself, which only ever holds the summary's raw counts.
 */
private fun SubcategoryProgress.ringContentDescription(resources: Resources, cardCount: Int): String =
    when (this) {
        Unresolved -> resources.getString(R.string.category_details_topic_progress_unavailable_cd)
        is Resolved -> resources.getString(
            R.string.category_details_topic_progress_cd,
            (studiedFraction(cardCount) * PROGRESS_PERCENT_SCALE).toInt(),
        )
    }

/** The ring's fill, `0f` for a topic with no cards rather than dividing by zero. */
private fun Resolved.studiedFraction(cardCount: Int): Float =
    if (cardCount > 0) studiedCount / cardCount.toFloat() else 0f

/**
 * A subcategory (topic) row, shaped by [isSelectionMode]:
 *
 * - **Default mode** (ADR-0041): progress ring leading, two separate destinations trailing — the
 *   play button jumps straight into the Preview Study Session Screen for this one topic while the
 *   row itself drills into Subcategory Details and starts nothing. Long-pressing enters Selection
 *   Mode with this topic selected.
 * - **Selection Mode**: the play button and chevron are gone — starting a single-topic session
 *   mid-selection would throw away the selection being assembled — and the row becomes a
 *   checkbox, still leading with the same ring, so its identity doesn't jump as the mode changes.
 *
 * The ring and the subtitle both derive from [progress] and this topic's own
 * [cardCount] — see [CategoryDetailsScreenState.progressFor].
 */
@Suppress("LongParameterList") // one callback per hoisted ViewModel action; a holder class would only rename the sprawl.
private fun Subcategory.toListGroupItem(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    progress: SubcategoryProgress,
    playContentDescription: String,
    ringContentDescription: String,
    cardCountLabel: String,
    studiedText: String?,
    rowSubtitleSeparator: String,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (Subcategory) -> Unit,
    onLongPress: (String) -> Unit,
    onSelectedChange: (String, Boolean) -> Unit,
): FlashcardsListGroupItem {
    val subcategory = this
    val ringFraction = (progress as? Resolved)?.studiedFraction(subcategory.cardCount)
    val ring: @Composable () -> Unit = {
        FlashcardsProgressRing(
            progress = ringFraction,
            contentDescription = ringContentDescription,
        )
    }
    val subtitle: @Composable () -> Unit = {
        CategoryDetailsRowSubtitle(cardCountLabel = cardCountLabel, studiedText = studiedText, separator = rowSubtitleSeparator)
    }
    return if (isSelectionMode) {
        FlashcardsListGroupItem.Selectable(
            key = subcategory.id,
            title = subcategory.name,
            subtitleContent = subtitle,
            selected = isSelected,
            onSelectedChange = { selected -> onSelectedChange(subcategory.id, selected) },
            leading = ring,
        )
    } else {
        FlashcardsListGroupItem.Row(
            key = subcategory.id,
            title = subcategory.name,
            secondaryContent = subtitle,
            onClick = {
                onNavigateToSubcategoryDetails(subcategory.categoryId, subcategory.categoryName, subcategory.id, subcategory.name)
            },
            onLongClick = { onLongPress(subcategory.id) },
            leading = ring,
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlashcardsIconButton(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = playContentDescription,
                        onClick = { onNavigateToPreviewStudySession(subcategory) },
                        size = FlashcardsComponentSize.Small,
                    )
                    FlashcardsChevron()
                }
            },
        )
    }
}

/**
 * `"<cards> · <studied>"`, forced to one line, ellipsized on overflow — never a marquee; that's
 * the title's job (see [FlashcardsListRow]/[FlashcardsSelectableListRow]), and a secondary line
 * scrolling alongside it would be two competing motions on one row. Card count is plain/neutral
 * text; studied is the brand accent [FlashcardsProgressRing]'s fill uses. Color is decoration on
 * top of text that already states the word, so nothing here is color-only.
 *
 * [studiedText] `null` (nothing studied yet — resolving, or resolved-but-zero) drops the studied
 * segment entirely rather than showing a dash or "0 studied": `"<cards>"` alone.
 */
@Composable
private fun CategoryDetailsRowSubtitle(cardCountLabel: String, studiedText: String?, separator: String) {
    val text = if (studiedText != null) {
        val studiedColor = MaterialTheme.colorScheme.primary
        buildAnnotatedString {
            append(cardCountLabel)
            append(separator)
            withStyle(SpanStyle(color = studiedColor)) { append(studiedText) }
        }
    } else {
        buildAnnotatedString { append(cardCountLabel) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
