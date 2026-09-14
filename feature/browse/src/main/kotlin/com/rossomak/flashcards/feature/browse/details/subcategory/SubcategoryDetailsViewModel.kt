package com.rossomak.flashcards.feature.browse.details.subcategory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.StudySessionPreference
import com.rossomak.flashcards.core.domain.model.orderedBy
import com.rossomak.flashcards.core.domain.usecase.FilterFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.composables.dialogs.selectAllTags
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SubcategoryDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getFlashcards: GetFlashcardsUseCase,
    private val filterFlashcards: FilterFlashcardsUseCase,
    private val observeStudySessionPreferences: ObserveStudySessionPreferencesUseCase,
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
) : ViewModel() {

    private val route = savedStateHandle.decodeRoute<SubcategoryDetailsRoute>()

    private val _state = MutableStateFlow(
        SubcategoryDetailsScreenState(
            categoryName = route.categoryName,
            subcategoryName = route.subcategoryName
        )
    )
    val state: StateFlow<SubcategoryDetailsScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<SubcategoryDetailsDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<SubcategoryDetailsMessage>(extraBufferCapacity = 1)

    /** Transient one-shot messages for the snackbar. Never screen state. */
    val messages: SharedFlow<SubcategoryDetailsMessage> = _messages.asSharedFlow()

    /**
     * The Subcategory's whole pool, or null until one has loaded. Nullable rather than empty: an
     * empty pool is not a thing a Subcategory can be, so "nothing loaded" needs its own value —
     * without it a dialog confirm after a failed load would re-render an empty pool as
     * [SubcategoryDetailsContentState.NoMatches] and quietly replace the error.
     */
    private var pool: List<Flashcard>? = null

    /**
     * Seeds the order from the user's saved preference **before** the first load, so the list is
     * already ordered when it appears rather than visibly re-sorting.
     *
     * A snapshot via [first], not a live collect: this screen can itself write that preference
     * through the Sort dialog's "keep as my default", and a live collect would let that write feed
     * straight back and clobber a later session-scoped change. It also means a preference edited
     * elsewhere does not retroactively re-sort a list already sitting on the back stack. Same
     * reasoning, same shape as the Preview screen's own seeding.
     *
     * Filters are exempt: they are never a saved default (ADR-0030), so they start cleared.
     */
    init {
        viewModelScope.launch {
            val defaults = observeStudySessionPreferences().first()
            _state.update { it.copy(sortOrder = defaults.sortOrder) }
            loadFlashcards()
        }
    }

    /** Single entry point for every dialog on this screen (ADR-0036). */
    fun onDialogEvent(event: SubcategoryDetailsDialogEvent) {
        when (event) {
            is Open -> _state.update { it.copy(activeDialog = event.dialog) }
            is DraftChange -> _state.update { it.copy(activeDialog = event.dialog) }
            Confirm -> onDialogConfirm()
            Dismiss -> _state.update { it.copy(activeDialog = null) }
        }
    }

    /**
     * Restores every tag and the full difficulty range — and deliberately **leaves the sort order
     * alone**. Sort can never cause an empty result, the action is labelled "Reset filters", and
     * resetting it would silently undo an unrelated choice.
     *
     * Reseeds tags from the current [SubcategoryDetailsScreenState.availableTags] rather than a
     * fixed empty set: "all tags selected" is the default this screen starts from, not "no tags".
     */
    fun onResetFilters() {
        _state.update {
            it.copy(
                filters = FlashcardFilters(selectedTags = emptySet(), difficultyRange = SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS)
                    .selectAllTags(it.availableTags),
            )
        }
        renderContent()
    }

    /**
     * Deliberately fake. There is no favourites feature: this flips a flag that dies with the
     * ViewModel and shows a snackbar, and **writes nothing anywhere** — no repository, no use case,
     * no preference. Do not wire it to storage on the assumption that it is a half-finished
     * integration; making favourites real is its own piece of work.
     */
    fun onFavoriteToggle() {
        val isFavorite = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = isFavorite) }
        _messages.tryEmit(
            if (isFavorite) SubcategoryDetailsMessage.AddedToFavorites else SubcategoryDetailsMessage.RemovedFromFavorites
        )
    }

    /**
     * Undo on the favourite snackbar. Restores the value the toggle moved away from rather than
     * flipping whatever is current: a snackbar outlives the tap that raised it, so a blind flip
     * would invert a later, unrelated toggle. Emits no message of its own.
     */
    fun onFavoriteUndo(restoreTo: Boolean) {
        _state.update { it.copy(isFavorite = restoreTo) }
    }

    fun onStartSession() {
        val state = _state.value
        viewModelScope.launch {
            eventChannel.send(
                SubcategoryDetailsDestination.PreviewStudySession(
                    categoryId = route.categoryId,
                    categoryName = route.categoryName,
                    subcategoryId = route.subcategoryId,
                    subcategoryName = route.subcategoryName,
                    filterTagIds = state.filters.selectedTags.toList(),
                    difficultyRange = state.filters.difficultyRange,
                    sortOrder = state.sortOrder,
                )
            )
        }
    }

    /**
     * The only commit path. Dismissal discards, because the draft lives on the dialog field and dies
     * with it.
     */
    private fun onDialogConfirm() {
        when (val dialog = _state.value.activeDialog) {
            null -> return
            is SubcategoryDetailsDialog.CardsSortingOrder -> {
                if (dialog.keepAsDefault) {
                    viewModelScope.launch {
                        saveStudySessionPreference(StudySessionPreference.SortOrder(dialog.draftState))
                    }
                }
                _state.update { it.copy(sortOrder = dialog.draftState, activeDialog = null) }
            }
            is SubcategoryDetailsDialog.Filters -> _state.update { it.copy(filters = dialog.draftState, activeDialog = null) }
        }
        renderContent()
    }

    private fun loadFlashcards() {
        viewModelScope.launch {
            pool = null
            _state.update { it.copy(content = SubcategoryDetailsContentState.Loading) }
            getFlashcards(route.subcategoryId)
                .onSuccess { flashcards ->
                    pool = flashcards
                    renderContent(seedFilters = true)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            content = SubcategoryDetailsContentState.Error(R.string.subcategory_details_load_error_message),
                        )
                    }
                }
        }
    }

    /**
     * Derives the list area from the pool, the active filters and the current order.
     *
     * An empty result can only mean the filters excluded everything — a Subcategory always holds at
     * least one Flashcard — so it maps to [SubcategoryDetailsContentState.NoMatches] rather than an
     * empty card list. That inference only holds once a pool has actually loaded, which is why this
     * does nothing while [pool] is null: rendering mid-load or after a failure would turn Loading or
     * Error into a bogus "no cards match your filters".
     *
     * @param seedFilters set only by the very first successful load: materializes
     * [FlashcardFilters.selectedTags] to this pool's full tag vocabulary. The filter used for
     * *this* render still reads the pre-seed value — empty, which [FilterFlashcardsUseCase]
     * already treats as "match everything" — so the seed changes what the Filters dialog shows
     * next, not what this render matches.
     */
    private fun renderContent(seedFilters: Boolean = false) {
        val pool = pool ?: return
        viewModelScope.launch {
            val state = _state.value
            val filtered = filterFlashcards(
                FilterFlashcardsUseCase.Params(
                    tagIds = state.filters.selectedTags,
                    difficultyRange = state.filters.difficultyRange,
                    pool = pool,
                )
            )
            _state.update {
                it.copy(
                    content = if (filtered.cards.isEmpty()) {
                        SubcategoryDetailsContentState.NoMatches
                    } else {
                        SubcategoryDetailsContentState.Cards(filtered.cards.orderedBy(it.sortOrder))
                    },
                    availableTags = filtered.poolTags,
                    totalCount = filtered.totalCount,
                    filters = if (seedFilters) {
                        it.filters.selectAllTags(filtered.poolTags)
                    } else {
                        it.filters
                    },
                )
            }
        }
    }
}
