package com.rossomak.flashcards.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.usecase.GetCategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.SearchCategoriesParams
import com.rossomak.flashcards.core.domain.usecase.SearchCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
    private val searchCategories: SearchCategoriesUseCase,
    private val observeProgressSummary: ObserveProgressSummaryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseScreenState())
    val state: StateFlow<BrowseScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<BrowseNavigationDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        loadCategories()
        observeSearchQuery()
        collectProgressSummary()
    }

    fun onCategoriesRefresh() {
        loadCategories()
    }

    fun onCategorySelected(categoryId: String, categoryName: String) {
        viewModelScope.launch {
            eventChannel.send(BrowseNavigationDestination.CategoryDetails(categoryId, categoryName))
        }
    }

    fun onSubcategorySelect(subcategory: Subcategory) {
        viewModelScope.launch {
            eventChannel.send(
                BrowseNavigationDestination.SubcategoryDetails(
                    categoryId = subcategory.categoryId,
                    categoryName = subcategory.categoryName,
                    subcategoryId = subcategory.id,
                    subcategoryName = subcategory.name,
                )
            )
        }
    }

    fun onSubcategorySessionStart(subcategory: Subcategory) {
        viewModelScope.launch {
            eventChannel.send(
                BrowseNavigationDestination.PreviewStudySession(
                    categoryId = subcategory.categoryId,
                    categoryName = subcategory.categoryName,
                    subcategoryId = subcategory.id,
                    subcategoryName = subcategory.name,
                )
            )
        }
    }

    /**
     * [SyncSearchBarState][com.rossomak.flashcards.feature.browse.SyncSearchBarState] re-collects
     * the text field's current value into a fresh [LaunchedEffect][androidx.compose.runtime.LaunchedEffect]
     * whenever the screen re-enters composition (e.g. back-navigating from a search result), which
     * replays the same, unchanged [query]. A no-op guard here keeps that replay from wiping
     * [BrowseScreenState.searchStatus]: without it, it is reset immediately while
     * [observeSearchQuery]'s `distinctUntilChanged` sees no real change and never reruns the search
     * to repopulate it.
     *
     * [SearchStatus.Loading] is set here, synchronously, rather than left for [runSearch] to set
     * once the debounce elapses — a query already long enough to search is "loading" for the whole
     * debounce-plus-fetch window, not just the fetch. Setting it only in [runSearch] left that
     * window showing the stale previous status (typically [SearchStatus.Prompt] moments after
     * deleting a character below the minimum and back above it), which read as "type more
     * characters" for a query that was already long enough.
     */
    fun onSearchQueryChange(query: String) {
        _state.update { current ->
            if (query == current.searchQuery) {
                current
            } else {
                current.copy(
                    searchQuery = query,
                    searchStatus = if (query.meetsSearchMinimumLength()) SearchStatus.Loading else SearchStatus.Prompt,
                )
            }
        }
    }

    fun onSearchActivate() {
        _state.update { it.copy(isSearchActive = true) }
    }

    /** Leaves search entirely. Clearing the text alone is the search field's own concern. */
    fun onSearchDismiss() {
        _state.update {
            it.copy(
                searchQuery = "",
                isSearchActive = false,
                searchStatus = SearchStatus.Prompt,
            )
        }
    }

    /**
     * The search query lives in screen state rather than a separate input flow, so the text field
     * and this pipeline can't disagree about what was typed. `distinctUntilChanged` sits *before*
     * `debounce` so that unrelated state changes (a category load finishing, a navigation event)
     * don't restart the debounce timer for a query that never changed.
     *
     * A query below the minimum length is skipped here rather than handled inside [runSearch]:
     * [onSearchQueryChange] already set [SearchStatus.Prompt] for it synchronously, so there is
     * nothing left to do once the debounce elapses.
     */
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _state
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collectLatest { query ->
                    if (query.meetsSearchMinimumLength()) {
                        runSearch(query)
                    }
                }
        }
    }

    /**
     * Only ever called with a query already known to meet the minimum length.
     *
     * [debounce] sitting upstream of [collectLatest] in [observeSearchQuery] only cancels a
     * previous [runSearch] once the *next* debounce window elapses — not the instant the query
     * changes. A search already in flight when the query changes again can therefore complete
     * during that window and land here for a query that is no longer current. Both completion
     * branches guard against that by checking [query] is still [BrowseScreenState.searchQuery]
     * before touching [BrowseScreenState.searchStatus], so a stale result or error can never
     * clobber the status the newer, still-running (or already-superseding) query set.
     */
    private suspend fun runSearch(query: String) {
        searchCategories(SearchCategoriesParams(query = query, categories = _state.value.categories))
            .onSuccess { results ->
                _state.update {
                    if (it.searchQuery != query) {
                        it
                    } else {
                        it.copy(searchStatus = if (results.isEmpty) SearchStatus.NoMatch else SearchStatus.Results(results))
                    }
                }
            }
            .onFailure {
                _state.update { current ->
                    if (current.searchQuery != query) current else current.copy(searchStatus = SearchStatus.Error)
                }
            }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getCategories()
                .onSuccess { categories ->
                    _state.update { it.copy(isLoading = false, categories = categories) }
                    // A query typed before this load completed ran against an empty category
                    // list, so it matched Subcategories but never their parent Categories. Rerun
                    // it now that categories are in state — distinctUntilChanged() upstream won't,
                    // since the query text itself hasn't changed.
                    rerunActiveSearch()
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    private suspend fun rerunActiveSearch() {
        val query = _state.value.searchQuery
        if (_state.value.isSearchActive && query.meetsSearchMinimumLength()) {
            _state.update { it.copy(searchStatus = SearchStatus.Loading) }
            runSearch(query)
        }
    }

    /** Whether [this], once trimmed, is long enough to actually run a search. */
    private fun String.meetsSearchMinimumLength(): Boolean =
        trim().length >= SearchCategoriesUseCase.MIN_QUERY_LENGTH

    /**
     * Runs independently of [loadCategories] and any search, so a slow progress read never gates
     * the category list or search results — same rule as
     * [com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsViewModel.collectProgressSummary].
     * [ObserveProgressSummaryUseCase] is a live Firestore listener, not a one-shot read: it
     * re-attaches on its own after a network drop, so a search that only succeeded because
     * connectivity came back also gets its progress rings filled in, with no extra wiring here.
     */
    private fun collectProgressSummary() {
        viewModelScope.launch {
            observeProgressSummary().collect { summary ->
                _state.update { it.copy(progressSummary = summary, isProgressResolved = true) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 500L
    }
}
