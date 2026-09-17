package com.rossomak.flashcards.feature.browse.details.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.PinShortcutResult
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveCategoryFavoriteStateUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.PinCategoryShortcutUseCase
import com.rossomak.flashcards.core.domain.usecase.SetCategoryFavoriteUseCase
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.feature.browse.R
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsMessage.AddedToFavorites
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsMessage.RemovedFromFavorites
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSubcategories: GetSubcategoriesUseCase,
    private val observeProgressSummary: ObserveProgressSummaryUseCase,
    private val observeCategoryFavoriteState: ObserveCategoryFavoriteStateUseCase,
    private val setCategoryFavorite: SetCategoryFavoriteUseCase,
    private val pinCategoryShortcut: PinCategoryShortcutUseCase,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<CategoryDetailsRoute>()

    private val _state = MutableStateFlow(CategoryDetailsScreenState(categoryId = route.categoryId, categoryName = route.categoryName))
    val state: StateFlow<CategoryDetailsScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<CategoryDetailsDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<CategoryDetailsMessage>(extraBufferCapacity = 1)

    /** Transient one-shot messages for the snackbar. Never screen state. */
    val messages: SharedFlow<CategoryDetailsMessage> = _messages.asSharedFlow()

    init {
        loadSubcategories()
        collectProgressSummary()
        observeFavoriteState()
    }

    /**
     * Enters Selection Mode with nothing selected, or leaves it. Leaving sets the field back to
     * `null`, not an empty set — the selection is discarded, never parked, so re-entering later
     * starts clean.
     */
    fun onSelectionModeToggle() {
        _state.update {
            it.copy(selectedSubcategoryIds = if (it.isSelectionMode) null else emptySet())
        }
    }

    /** Long-pressing a subcategory enters Selection Mode **and** selects the pressed subcategory, in one update. */
    fun onSubcategoryLongPress(subcategoryId: String) {
        _state.update { it.copy(selectedSubcategoryIds = setOf(subcategoryId)) }
    }

    fun onSubcategorySelectionChange(subcategoryId: String, selected: Boolean) {
        _state.update {
            val current = it.selectedSubcategoryIds ?: return@update it
            it.copy(
                selectedSubcategoryIds = if (selected) current + subcategoryId else current - subcategoryId,
            )
        }
    }

    /**
     * Two-state on [CategoryDetailsScreenState.isAllSelected]: all selected clears to empty,
     * anything else — including a partial selection — selects every subcategory. There is no
     * indeterminate third state; the partial case and the empty case both want "select everything".
     */
    fun onSelectAllToggle() {
        _state.update {
            val subcategories = (it.content as? CategoryDetailsContentState.Subcategories)?.subcategories ?: return@update it
            it.copy(
                selectedSubcategoryIds = if (it.isAllSelected) {
                    emptySet()
                } else {
                    subcategories.map { subcategory -> subcategory.id }.toSet()
                },
            )
        }
    }

    /** Every Subcategory in the Category, sampled by the Preview screen — not honoured literally. */
    fun onQuickSessionStart() {
        val subcategories = (_state.value.content as? CategoryDetailsContentState.Subcategories)?.subcategories ?: return
        emitPreviewSession(subcategories = subcategories, isQuickSession = true)
    }

    /**
     * Exactly the selected Subcategories, honoured literally — not sampled. Emitted in **list
     * order, not selection order**, so a session's subcategory order does not depend on the order the
     * user happened to tap.
     */
    fun onCustomSessionStart() {
        val state = _state.value
        val selectedIds = state.selectedSubcategoryIds ?: return
        val subcategories = (state.content as? CategoryDetailsContentState.Subcategories)?.subcategories ?: return
        emitPreviewSession(
            subcategories = subcategories.filter { it.id in selectedIds },
            isQuickSession = false,
        )
    }

    private fun emitPreviewSession(subcategories: List<Subcategory>, isQuickSession: Boolean) {
        viewModelScope.launch {
            eventChannel.send(
                CategoryDetailsDestination.PreviewStudySession(
                    categoryId = route.categoryId,
                    categoryName = route.categoryName,
                    subcategoryIds = subcategories.map { it.id },
                    subcategoryNames = subcategories.map { it.name },
                    isQuickSession = isQuickSession,
                )
            )
        }
    }

    fun onFavoriteToggle() {
        val isFavorite = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = isFavorite) }
        viewModelScope.launch {
            val result = setCategoryFavorite(SetCategoryFavoriteUseCase.Params(route.categoryId, isFavorite))
            if (result.isSuccess) {
                _messages.tryEmit(if (isFavorite) AddedToFavorites else RemovedFromFavorites)
            }
        }
    }

    /**
     * Undo on the favourite snackbar. Restores the value the toggle moved away from rather than
     * flipping whatever is current: a snackbar outlives the tap that raised it, so a blind flip
     * would invert a later, unrelated toggle. Emits no message of its own.
     */
    fun onFavoriteUndo(restoreTo: Boolean) {
        _state.update { it.copy(isFavorite = restoreTo) }
        viewModelScope.launch {
            setCategoryFavorite(SetCategoryFavoriteUseCase.Params(route.categoryId, restoreTo))
        }
    }

    fun onAddShortcutClick() {
        viewModelScope.launch {
            when (pinCategoryShortcut(route.categoryId)) {
                PinShortcutResult.Pinned -> Unit
                PinShortcutResult.EntityNotFound -> _messages.tryEmit(CategoryDetailsMessage.ShortcutPinFailed)
                PinShortcutResult.UnsupportedLauncher -> _messages.tryEmit(CategoryDetailsMessage.ShortcutPinUnsupported)
            }
        }
    }

    private fun observeFavoriteState() {
        viewModelScope.launch {
            observeCategoryFavoriteState(route.categoryId).collect { isFavorite ->
                _state.update { it.copy(isFavorite = isFavorite) }
            }
        }
    }

    internal fun loadSubcategories() {
        viewModelScope.launch {
            _state.update { it.copy(content = CategoryDetailsContentState.Loading) }
            getSubcategories(route.categoryId)
                .onSuccess { subcategories ->
                    _state.update {
                        it.copy(
                            // A Category always contains at least one Subcategory (CONTEXT.md); an
                            // empty result means the read is lying, not that there's nothing to show.
                            content = if (subcategories.isEmpty()) {
                                CategoryDetailsContentState.Error(R.string.category_details_load_error)
                            } else {
                                CategoryDetailsContentState.Subcategories(subcategories)
                            },
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(content = CategoryDetailsContentState.Error(R.string.category_details_load_error)) }
                }
        }
    }

    /**
     * Runs independently of [loadSubcategories] so a slow progress read never gates the
     * subcategory list. [ObserveProgressSummaryUseCase] is a live Firestore listener, not a
     * one-shot read: it re-attaches on its own after a network drop, so a screen left open through
     * a connectivity blip still gets its rings filled in without any retry wiring here.
     */
    private fun collectProgressSummary() {
        viewModelScope.launch {
            observeProgressSummary().collect { summary ->
                _state.update { it.copy(progressSummary = summary, isProgressResolved = true) }
            }
        }
    }
}
