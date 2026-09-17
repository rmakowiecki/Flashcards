package com.rossomak.flashcards.feature.browse.details.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.usecase.GetProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveCategoryFavoriteStateUseCase
import com.rossomak.flashcards.core.domain.usecase.PinCategoryShortcutUseCase
import com.rossomak.flashcards.core.domain.usecase.SetCategoryFavoriteUseCase
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.feature.browse.R
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
    private val getProgressSummary: GetProgressSummaryUseCase,
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
        loadProgressSummary()
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
            it.copy(
                selectedSubcategoryIds = if (it.isAllSelected) {
                    emptySet()
                } else {
                    it.subcategories.map { subcategory -> subcategory.id }.toSet()
                },
            )
        }
    }

    /** Every Subcategory in the Category, sampled by the Preview screen — not honoured literally. */
    fun onQuickSessionStart() {
        emitPreviewSession(subcategories = _state.value.subcategories, isQuickSession = true)
    }

    /**
     * Exactly the selected Subcategories, honoured literally — not sampled. Emitted in **list
     * order, not selection order**, so a session's subcategory order does not depend on the order the
     * user happened to tap.
     */
    fun onCustomSessionStart() {
        val state = _state.value
        val selectedIds = state.selectedSubcategoryIds ?: return
        emitPreviewSession(
            subcategories = state.subcategories.filter { it.id in selectedIds },
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
                _messages.tryEmit(
                    if (isFavorite) {
                        CategoryDetailsMessage.AddedToFavorites
                    } else {
                        CategoryDetailsMessage.RemovedFromFavorites
                    },
                )
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

    /** `false` (unresolvable Category, or launcher doesn't support pinning) surfaces a snackbar instead of doing nothing. */
    fun onAddShortcutClick() {
        viewModelScope.launch {
            val pinned = pinCategoryShortcut(route.categoryId)
            if (!pinned) {
                _messages.tryEmit(CategoryDetailsMessage.ShortcutPinUnsupported)
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

    private fun loadSubcategories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorResId = null) }
            getSubcategories(route.categoryId)
                .onSuccess { subcategories ->
                    _state.update { it.copy(isLoading = false, subcategories = subcategories) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, errorResId = R.string.category_details_load_error) }
                }
        }
    }

    /**
     * Runs independently of [loadSubcategories] so a slow or failed progress read never gates the
     * subcategory list. A failure leaves [CategoryDetailsScreenState.isProgressResolved] `false`
     * forever — every ring stays unknown and every subtitle simply drops its studied segment
     * (same as a never-studied subcategory), with no error surfaced, per the ticket's "no error, no
     * retry prompt, no snackbar" rule.
     */
    private fun loadProgressSummary() {
        viewModelScope.launch {
            getProgressSummary()
                .onSuccess { summary ->
                    _state.update { it.copy(progressSummary = summary, isProgressResolved = true) }
                }
        }
    }
}
