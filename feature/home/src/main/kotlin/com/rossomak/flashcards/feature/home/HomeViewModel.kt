package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeFavoriteItems: ObserveFavoriteItemsUseCase,
    private val observeProgressSummary: ObserveProgressSummaryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<HomeDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        collectFavoriteItems()
        collectProgressSummary()
    }

    /** The card body browses: it opens Category Details and starts nothing (ADR-0041). */
    fun onFavoriteCategorySelect(category: Category) {
        viewModelScope.launch {
            eventChannel.send(HomeDestination.CategoryDetails(categoryId = category.id, categoryName = category.name))
        }
    }

    /** The card body browses: it opens Subcategory Details and starts nothing (ADR-0041). */
    fun onFavoriteSubcategorySelect(subcategory: Subcategory) {
        viewModelScope.launch {
            eventChannel.send(
                HomeDestination.SubcategoryDetails(
                    categoryId = subcategory.categoryId,
                    categoryName = subcategory.categoryName,
                    subcategoryId = subcategory.id,
                    subcategoryName = subcategory.name,
                )
            )
        }
    }

    /** Sends only the Category: Preview samples the Subcategories itself, so Home loads none (ADR-0056). */
    fun onFavoriteCategoryQuickSessionStart(category: Category) {
        viewModelScope.launch {
            eventChannel.send(
                HomeDestination.QuickSessionPreviewStudySession(categoryId = category.id, categoryName = category.name)
            )
        }
    }

    /** The card's play button studies: a single-subcategory session for just this Subcategory (ADR-0041). */
    fun onFavoriteSubcategorySessionStart(subcategory: Subcategory) {
        viewModelScope.launch {
            eventChannel.send(
                HomeDestination.SubcategoryPreviewStudySession(
                    categoryId = subcategory.categoryId,
                    categoryName = subcategory.categoryName,
                    subcategoryId = subcategory.id,
                    subcategoryName = subcategory.name,
                )
            )
        }
    }

    /**
     * Both observed flows are live Firestore listeners that retry only a permission-denied error, so any
     * other failure is caught here instead of crashing out of [viewModelScope]. A failure before the
     * first emission degrades to [Empty], the same rule the use case applies to a failed id fetch; a
     * failure after one leaves the [Content] already shown alone.
     */
    private fun collectFavoriteItems() {
        viewModelScope.launch {
            observeFavoriteItems()
                .catch { error ->
                    loge(error) { "Observing Favorites failed" }
                    _state.update { current ->
                        if (current.favorites is Loading) current.copy(favorites = Empty) else current
                    }
                }
                .collect { favoriteItems ->
                    _state.update { current ->
                        current.copy(favorites = if (favoriteItems.isEmpty()) Empty else Content(favoriteItems))
                    }
                }
        }
    }

    /**
     * Runs independently of [collectFavoriteItems] so a slow or failing progress read never gates the
     * cards. A failure leaves the resolved flag as it was: Subcategory cards keep their unresolved state.
     */
    private fun collectProgressSummary() {
        viewModelScope.launch {
            observeProgressSummary()
                .catch { error -> loge(error) { "Observing the progress summary failed" } }
                .collect { summary ->
                    _state.update { it.copy(progressSummary = summary, isProgressResolved = true) }
                }
        }
    }
}
