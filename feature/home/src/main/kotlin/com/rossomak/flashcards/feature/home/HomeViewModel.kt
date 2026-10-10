package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.toReplay
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveRecentSessionsUseCase
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Hidden
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Hidden as RecentsHidden
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
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
    private val observeRecentSessions: ObserveRecentSessionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<HomeDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        collectFavoriteItems()
        collectProgressSummary()
        collectRecentSessions()
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
     * A Recent row has no browse target, so it opens Preview to study the same thing again (ADR-0041 exception).
     * Quick sends no Subcategories, so Preview samples the Category again.
     */
    fun onRecentSelect(item: RecentItem) {
        val replay = item.session.toReplay()
        viewModelScope.launch {
            eventChannel.send(
                HomeDestination.RecentPreviewStudySession(
                    categoryId = replay.categoryId,
                    categoryName = replay.categoryName,
                    sourceType = replay.sourceType,
                    subcategoryIds = replay.subcategoryIds,
                    subcategoryNames = replay.subcategoryNames,
                    studyMode = replay.studyMode,
                    voiceAnsweringEnabled = replay.voiceAnsweringEnabled,
                    readAloudEnabled = replay.readAloudEnabled,
                )
            )
        }
    }

    /**
     * Both observed flows are live Firestore listeners that retry only a permission-denied error, so any
     * other failure is caught here instead of crashing out of [viewModelScope]. A failure before the
     * first emission degrades to [Hidden], the same rule the use case applies to a failed id fetch; a
     * failure after one leaves the [Content] already shown alone.
     */
    private fun collectFavoriteItems() {
        viewModelScope.launch {
            observeFavoriteItems()
                .catch { error ->
                    loge(error) { "Observing Favorites failed" }
                    _state.update { current ->
                        if (current.favorites is Loading) current.copy(favorites = Hidden) else current
                    }
                }
                .collect { favoriteItems ->
                    _state.update { current ->
                        current.copy(favorites = if (favoriteItems.isEmpty()) Hidden else Content(favoriteItems))
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

    /**
     * Runs independently of [collectFavoriteItems] and [collectProgressSummary] so neither section gates the
     * other. Like Favorites, a failure before the first emission degrades to [RecentsHidden], and a failure
     * after one leaves the [RecentsContent] already shown alone.
     */
    private fun collectRecentSessions() {
        viewModelScope.launch {
            observeRecentSessions()
                .catch { error ->
                    loge(error) { "Observing Recents failed" }
                    _state.update { current ->
                        if (current.recents is RecentsLoading) current.copy(recents = RecentsHidden) else current
                    }
                }
                .collect { recentItems ->
                    _state.update { current ->
                        current.copy(recents = if (recentItems.isEmpty()) RecentsHidden else RecentsContent(recentItems))
                    }
                }
        }
    }
}
