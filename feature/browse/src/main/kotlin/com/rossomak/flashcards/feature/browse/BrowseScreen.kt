package com.rossomak.flashcards.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CategorySearchResults
import com.rossomak.flashcards.core.domain.model.CategoryWithSubcategorySummary
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.FlashcardsVectorIconTile
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsDetailedListRow
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress
import com.rossomak.flashcards.feature.browse.details.category.subcategoryProgressFor
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
    onNavigateToCategoryDetails: (String, String) -> Unit,
    onNavigateToSubcategoryDetails: (String, String, String, String) -> Unit,
    onNavigateToPreviewStudySession: (String, String, String, String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is BrowseNavigationDestination.CategoryDetails ->
                onNavigateToCategoryDetails(destination.categoryId, destination.categoryName)

            is BrowseNavigationDestination.SubcategoryDetails -> onNavigateToSubcategoryDetails(
                destination.categoryId,
                destination.categoryName,
                destination.subcategoryId,
                destination.subcategoryName,
            )

            is BrowseNavigationDestination.PreviewStudySession -> onNavigateToPreviewStudySession(
                destination.categoryId,
                destination.categoryName,
                destination.subcategoryId,
                destination.subcategoryName,
            )
        }
    }

    val searchActions = remember(viewModel) {
        BrowseSearchActions(
            onQueryChange = viewModel::onSearchQueryChange,
            onActivate = viewModel::onSearchActivate,
            onDismiss = viewModel::onSearchDismiss,
        )
    }

    BrowseContent(
        modifier = modifier,
        state = state,
        onRefresh = viewModel::onCategoriesRefresh,
        searchActions = searchActions,
        onCategoryClick = viewModel::onCategorySelect,
        onSubcategoryClick = viewModel::onSubcategorySelect,
        onSubcategorySessionStart = viewModel::onSubcategorySessionStart,
    )
}

/**
 * Hosts Material 3's search bar in its intended pairing: a collapsed [TopSearchBar] above the
 * category list, and an [ExpandedFullScreenSearchBar] that takes over the window to show results.
 *
 * Expansion is owned by `SearchBarState` and the query text by a `TextFieldState`, both of which
 * M3 requires; [state] and [searchActions] remain the source of truth for everything downstream of
 * the query, so the debounce, minimum length, cache and matching rules are untouched by this.
 *
 * Consequences of the expanded bar being a dialog window, all deliberate: the bottom navigation
 * bar is hidden while searching, back and predictive-back are handled by the dialog rather than a
 * `BackHandler` here, and the keyboard insets are the dialog's problem, so no `imePadding()` is
 * needed on the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseContent(
    modifier: Modifier = Modifier,
    state: BrowseScreenState,
    onRefresh: () -> Unit,
    searchActions: BrowseSearchActions,
    onCategoryClick: (String, String) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    SyncSearchBarState(
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        searchActions = searchActions,
    )

    val barColors = containedSearchBarColors()

    val progressFor = rememberProgressFor(state.progressSummary, state.isProgressResolved)

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            // Results update as the user types and live inside the expanded bar, so submitting has
            // nothing to do — collapsing here would hide the very results being asked for.
            onSearch = {},
            colors = barColors.inputFieldColors,
            placeholder = { Text(text = stringResource(R.string.browse_search_hint)) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            coroutineScope.launch { searchBarState.animateToCollapsed() }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.common_search_dismiss_cd),
                        )
                    }
                } else {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(CoreUiR.string.common_search_clear_cd),
                        )
                    }
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopSearchBar(state = searchBarState, inputField = inputField, colors = barColors)
        Box(modifier = Modifier.fillMaxSize()) {
            CategoryListContent(
                isLoading = state.isLoading,
                categories = state.categories,
                favorites = state.favorites,
                onRefresh = onRefresh,
                onCategoryClick = onCategoryClick,
            )
        }
    }

    // Renders nothing until expanded — it early-returns on the collapsed state — so calling it
    // unconditionally is safe.
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = barColors,
    ) {
        ExpandedSearchContent(
            searchStatus = state.searchStatus,
            categories = state.categories,
            favorites = state.favorites,
            progressFor = progressFor,
            onCategoryClick = onCategoryClick,
            onSubcategoryClick = onSubcategoryClick,
            onSubcategorySessionStart = onSubcategorySessionStart,
        )
    }
}

/**
 * Keyed on the two fields it reads, so a search keystroke hands the results the same function
 * instance and unchanged rows skip. A `state::progressFor` reference would capture the whole state
 * and be new on every recomposition.
 */
@Composable
private fun rememberProgressFor(progressSummary: ProgressSummary?, isProgressResolved: Boolean): (String) -> SubcategoryProgress =
    remember(progressSummary, isProgressResolved) {
        { subcategoryId -> progressSummary.subcategoryProgressFor(subcategoryId, isProgressResolved) }
    }

/**
 * The three things this content slot can show: a spinner during the initial load, an error card,
 * or the category list. An empty [categories] always reads as failure — whether
 * a real load error or genuinely zero categories, there's nothing useful to show and retry is the
 * only recourse — so both collapse into the same error state.
 *
 * Takes only the fields it renders rather than the whole screen state, so a search keystroke
 * (which changes only the search fields) doesn't recompose the list hidden under the search bar.
 */
@Composable
private fun BoxScope.CategoryListContent(
    isLoading: Boolean,
    categories: List<Category>,
    favorites: UserFavorites,
    onRefresh: () -> Unit,
    onCategoryClick: (String, String) -> Unit,
) {
    when {
        isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        categories.isEmpty() -> CenteredEmptyState(
            icon = Icons.Filled.ErrorOutline,
            title = stringResource(R.string.browse_categories_error_title),
            supportingText = stringResource(R.string.browse_categories_error_message),
            tone = FlashcardsEmptyStateTone.Error,
            button = {
                FlashcardsFilledButton(
                    text = stringResource(CoreUiR.string.common_retry_button),
                    onClick = onRefresh,
                    icon = Icons.Filled.Refresh,
                )
            },
        )
        else -> CategoryList(categories = categories, favorites = favorites, onCategoryClick = onCategoryClick)
    }
}

/**
 * Material 3 owns two pieces of state this screen does not: the query text lives in a
 * [TextFieldState] and expansion in a [SearchBarState]. Both are forwarded to the ViewModel here,
 * so the debounce, minimum length, cache and matching rules keep running off screen state rather
 * than a second, parallel copy.
 *
 * Emptying the field on collapse stops a stale query surviving out of sight and repopulating the
 * results the next time the bar is opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSearchBarState(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    searchActions: BrowseSearchActions,
) {
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(searchActions.onQueryChange)
    }
    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Expanded) {
            searchActions.onActivate()
        } else {
            textFieldState.clearText()
            searchActions.onDismiss()
        }
    }
}

/**
 * Material 3 1.4.0 ships only the edge-to-edge full-screen style: `FullScreenSearchBarLayout` pads
 * the input field by window insets alone and always emits a `HorizontalDivider` above the content.
 * The "contained" style is reached with three adjustments — a transparent divider, since it cannot
 * be opted out of; a bar surface distinct from the input field's container, which
 * `SearchBarDefaults.colors()` otherwise paints identically and so dissolves the expanded pill into
 * a flat bar; and a horizontal inset on the input field, applied at the call site.
 *
 * Configuration rather than a supported style flag: if a later Material 3 release adds a real
 * contained variant, this is what it replaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun containedSearchBarColors(): SearchBarColors = SearchBarDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface,
    dividerColor = Color.Transparent,
    inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ),
)

/** The five things the expanded bar can show, one per [SearchStatus]. */
@Composable
private fun ExpandedSearchContent(
    searchStatus: SearchStatus,
    categories: List<Category>,
    favorites: UserFavorites,
    progressFor: (String) -> SubcategoryProgress,
    onCategoryClick: (String, String) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
) {
    when (searchStatus) {
        SearchStatus.Prompt -> CenteredEmptyState(
            icon = Icons.Filled.Search,
            title = stringResource(R.string.browse_search_prompt_title),
            supportingText = stringResource(R.string.browse_search_prompt_message),
        )

        SearchStatus.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is SearchStatus.Results -> SearchResults(
            results = searchStatus.results,
            categories = categories,
            favorites = favorites,
            progressFor = progressFor,
            onCategoryClick = onCategoryClick,
            onSubcategoryClick = onSubcategoryClick,
            onSubcategorySessionStart = onSubcategorySessionStart,
        )

        SearchStatus.NoMatch -> CenteredEmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.browse_search_no_results_title),
            supportingText = stringResource(R.string.browse_search_no_results_message),
        )

        SearchStatus.Error -> CenteredEmptyState(
            icon = Icons.Filled.ErrorOutline,
            title = stringResource(R.string.browse_search_error_title),
            supportingText = stringResource(R.string.browse_search_error_message),
            tone = FlashcardsEmptyStateTone.Error,
        )
    }
}

/**
 * [FlashcardsEmptyState] does not size or center itself by design (see its own doc), so every call
 * site otherwise repeats the same fill-and-center `Box`. Centralized here rather than duplicated
 * per branch above.
 */
@Composable
private fun CenteredEmptyState(
    icon: ImageVector,
    title: String,
    supportingText: String,
    tone: FlashcardsEmptyStateTone = FlashcardsEmptyStateTone.Info,
    button: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FlashcardsEmptyState(
            icon = icon,
            title = title,
            supportingText = supportingText,
            tone = tone,
            button = button,
        )
    }
}

/**
 * Categories are a short, fixed set (roughly a dozen) so this binds [FlashcardsListGroup]
 * directly rather than a `LazyColumn` — every row composes up front at negligible cost. A
 * subcategory list nested under one category can run into the dozens and should use
 * `flashcardsListGroupItems` inside a `LazyColumn` instead.
 *
 * Rows read [Category] directly through the builder form of [FlashcardsListGroup], so there is no
 * per-composition mapping into an intermediate row model.
 *
 * Callers only reach this with a non-empty [categories]: the empty case is handled upstream in
 * [CategoryListContent] as an error state.
 */
@Composable
internal fun CategoryList(
    categories: List<Category>,
    favorites: UserFavorites,
    onCategoryClick: (String, String) -> Unit,
) {
    ScrollableSectionColumn {
        FlashcardsOverlineLabel(text = stringResource(R.string.browse_categories_label))
        FlashcardsListGroup(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
            items = categories,
            key = { category -> category.id },
        ) { category, rowModifier ->
            CategoryRow(
                modifier = rowModifier,
                category = category,
                // Outside search there is nothing to hoist, so a category's chip line is exactly
                // its stored prominence order.
                subcategorySummary = category.featuredSubcategoryNames,
                isFavorited = favorites.categoryIds.containsKey(category.id),
                onCategoryClick = onCategoryClick,
            )
        }
    }
}

/**
 * Matched Subcategories above matched categories, each under its own section header. Both
 * sections are bounded — Subcategories by the search query's page limit, categories by how many
 * exist — so neither needs a lazy container.
 *
 * Callers only reach this with a non-empty [results]: an empty result is [SearchStatus.NoMatch],
 * rendered upstream in [ExpandedSearchContent] instead.
 */
@Composable
internal fun SearchResults(
    results: CategorySearchResults,
    categories: List<Category>,
    favorites: UserFavorites,
    progressFor: (String) -> SubcategoryProgress,
    onCategoryClick: (String, String) -> Unit,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
) {
    ScrollableSectionColumn {
        if (results.subcategories.isNotEmpty()) {
            FlashcardsOverlineLabel(text = stringResource(R.string.browse_topics_label))
            SubcategoryListGroup(
                subcategories = results.subcategories,
                categories = categories,
                favorites = favorites,
                progressFor = progressFor,
                onSubcategoryClick = onSubcategoryClick,
                onSubcategorySessionStart = onSubcategorySessionStart,
            )
        }
        if (results.categories.isNotEmpty()) {
            FlashcardsOverlineLabel(text = stringResource(R.string.browse_categories_label))
            FlashcardsListGroup(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
                items = results.categories,
                key = { categoryWithSummary -> categoryWithSummary.category.id },
            ) { categoryWithSummary, rowModifier ->
                CategoryRow(
                    modifier = rowModifier,
                    category = categoryWithSummary.category,
                    subcategorySummary = categoryWithSummary.subcategorySummary,
                    isFavorited = favorites.categoryIds.containsKey(categoryWithSummary.category.id),
                    onCategoryClick = onCategoryClick,
                )
            }
        }
    }
}

@Composable
private fun SubcategoryListGroup(
    subcategories: List<Subcategory>,
    categories: List<Category>,
    favorites: UserFavorites,
    progressFor: (String) -> SubcategoryProgress,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
) {
    val categoriesById = remember(categories) { categories.associateBy { it.id } }
    FlashcardsListGroup(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
        items = subcategories,
        key = { subcategory -> subcategory.id },
    ) { subcategory, rowModifier ->
        SubcategorySearchResultRow(
            modifier = rowModifier,
            subcategory = subcategory,
            progress = progressFor(subcategory.id),
            // Not found only for a stale/inconsistent categoryId — falls back to the glyph's own
            // generic icon, same as a category with no iconSvg curated yet.
            iconSvg = categoriesById[subcategory.categoryId]?.iconSvg,
            isFavorited = favorites.subcategoryIds.containsKey(subcategory.id),
            onSubcategoryClick = onSubcategoryClick,
            onSubcategorySessionStart = onSubcategorySessionStart,
        )
    }
}

@Composable
private fun ScrollableSectionColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MaterialTheme.spacing.xsmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        content()
    }
}

/**
 * The subtitle line is the category's subcategory-summary chip line, e.g.
 * `Compose · Coroutines · Testing`. The placeholder subtitle only shows for a category with no
 * Subcategories to name at all — every other row names its most prominent Subcategories.
 */
@Composable
private fun CategoryRow(
    modifier: Modifier,
    category: Category,
    subcategorySummary: List<String>,
    isFavorited: Boolean,
    onCategoryClick: (String, String) -> Unit,
) {
    val subcategorySummarySeparator = stringResource(CoreUiR.string.common_middle_dot_separator)
    val placeholderSubtitle = stringResource(R.string.browse_category_placeholder_subtitle)
    FlashcardsDetailedListRow(
        modifier = modifier,
        title = category.name,
        subtitle = subcategorySummary.joinToString(subcategorySummarySeparator).ifEmpty { placeholderSubtitle },
        secondaryText = pluralStringResource(
            R.plurals.browse_category_topic_count,
            category.subcategoryCount,
            category.subcategoryCount,
        ),
        onClick = { onCategoryClick(category.id, category.name) },
        isFavorited = isFavorited,
        leading = {
            FlashcardsVectorIconTile(
                iconSvg = category.iconSvg,
                color = category.color,
                contentDescription = null,
            )
        },
        trailing = { FlashcardsChevron() },
    )
}

private val previewSearchActions = BrowseSearchActions(
    onQueryChange = {},
    onActivate = {},
    onDismiss = {},
)

private val previewCategories = listOf(
    Category(
        id = "android",
        name = "Android",
        order = 0,
        subcategoryCount = 13,
        iconSvg = null,
        color = "#6B2FA0",
        featuredSubcategoryNames = listOf("Compose", "Coroutines", "Compose Navigation"),
    ),
    Category(
        id = "python",
        name = "Python",
        order = 1,
        subcategoryCount = 20,
        iconSvg = null,
        color = "#0277BD",
        featuredSubcategoryNames = listOf("Async", "Typing", "Standard Library"),
    ),
    Category(
        id = "ios",
        name = "iOS",
        order = 2,
        subcategoryCount = 8,
        iconSvg = null,
        color = "#00838F",
        featuredSubcategoryNames = listOf("SwiftUI", "Combine", "Core Data"),
    ),
)

private val previewAndroidSubcategories = listOf(
    Subcategory(
        id = "android-compose",
        name = "Compose",
        categoryId = "android",
        categoryName = "Android",
        order = 0,
        cardCount = 120,
    ),
    Subcategory(
        id = "android-compose-navigation",
        name = "Compose Navigation",
        categoryId = "android",
        categoryName = "Android",
        order = 2,
        cardCount = 34,
    ),
)

@Preview(showBackground = true)
@Composable
private fun BrowseContentPreview() {
    BrowseContent(
        state = BrowseScreenState(categories = previewCategories),
        onRefresh = {},
        searchActions = previewSearchActions,
        onCategoryClick = { _, _ -> },
        onSubcategoryClick = {},
        onSubcategorySessionStart = {},
    )
}

/**
 * Results preview the sections directly rather than through [BrowseContent]: they render inside
 * [ExpandedFullScreenSearchBar]'s dialog window at runtime, which a `@Preview` cannot show.
 *
 * [previewProgressFor] shows one subcategory resolved with real progress and one never studied, so both
 * subtitle shapes are visible at once.
 */
@Preview(showBackground = true)
@Composable
private fun SearchResultsPreview() {
    val previewProgressFor: (String) -> SubcategoryProgress = { subcategoryId ->
        if (subcategoryId == previewAndroidSubcategories.first().id) {
            SubcategoryProgress.Resolved(studiedCount = 25, masteredCount = 10)
        } else {
            SubcategoryProgress.Resolved(studiedCount = 0, masteredCount = 0)
        }
    }
    SearchResults(
        results = CategorySearchResults(
            subcategories = previewAndroidSubcategories,
            categories = listOf(
                CategoryWithSubcategorySummary(
                    category = previewCategories.first(),
                    subcategorySummary = listOf("Compose", "Compose Navigation", "Coroutines"),
                ),
            ),
        ),
        categories = previewCategories,
        favorites = UserFavorites.EMPTY,
        progressFor = previewProgressFor,
        onCategoryClick = { _, _ -> },
        onSubcategoryClick = {},
        onSubcategorySessionStart = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchNoMatchPreview() {
    CenteredEmptyState(
        icon = Icons.Filled.SearchOff,
        title = stringResource(R.string.browse_search_no_results_title),
        supportingText = stringResource(R.string.browse_search_no_results_message),
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchPromptPreview() {
    CenteredEmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(R.string.browse_search_prompt_title),
        supportingText = stringResource(R.string.browse_search_prompt_message),
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchErrorPreview() {
    CenteredEmptyState(
        icon = Icons.Filled.ErrorOutline,
        title = stringResource(R.string.browse_search_error_title),
        supportingText = stringResource(R.string.browse_search_error_message),
        tone = FlashcardsEmptyStateTone.Error,
    )
}
