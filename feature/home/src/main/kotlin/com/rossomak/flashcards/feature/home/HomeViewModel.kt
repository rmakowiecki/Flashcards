package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Resolved
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Unresolved
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession.Fast
import com.rossomak.flashcards.core.domain.model.RecentSession.Rated
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.UserPreference.HasHiddenFavoritesHint
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveLevelProgressUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveRecentSessionsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content as FavoritesContent
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty as FavoritesEmpty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed as FavoritesFailed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading as FavoritesLoading
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Content as LevelCardContent
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Loading as LevelCardLoading
import com.rossomak.flashcards.feature.home.HomeLevelCardState.Unavailable as LevelCardUnavailable
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeAuthUser: ObserveAuthUserUseCase,
    private val observeFavoriteItems: ObserveFavoriteItemsUseCase,
    private val observeLevelProgress: ObserveLevelProgressUseCase,
    private val observeProgressSummary: ObserveProgressSummaryUseCase,
    private val observeRecentSessions: ObserveRecentSessionsUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<HomeDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    // One handle per section collector: a thrown failure or a completion leaves its flow dead, so Retry relaunches it.
    private var levelCardJob: Job? = null
    private var favoritesJob: Job? = null
    private var recentsJob: Job? = null
    private var revealCeilingJob: Job? = null

    init {
        startRevealCeiling()
        collectLevelCard()
        collectFavoriteItems()
        collectProgressSummary()
        collectRecentSessions()
        collectFavoritesHintPreference()
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
        val session = item.session
        val (subcategoryIds, subcategoryNames) = when (session.sourceType) {
            SingleSubcategory, Custom -> session.subcategoryIds to session.subcategoryNames
            Quick -> emptyList<String>() to emptyList()
        }
        val (voiceAnsweringEnabled, readAloudEnabled) = when (session) {
            is Rated -> session.voiceAnsweringEnabled to null
            is Fast -> null to session.readAloudEnabled
        }
        viewModelScope.launch {
            eventChannel.send(
                HomeDestination.RecentPreviewStudySession(
                    categoryId = session.categoryId,
                    categoryName = session.categoryName,
                    sourceType = session.sourceType,
                    subcategoryIds = subcategoryIds,
                    subcategoryNames = subcategoryNames,
                    studyMode = session.mode,
                    voiceAnsweringEnabled = voiceAnsweringEnabled,
                    readAloudEnabled = readAloudEnabled,
                )
            )
        }
    }

    /**
     * Relaunches only the sections that [Failed][FavoritesFailed], after setting them back to Loading, so the
     * Retry button goes away at once and a second tap finds nothing to retry. Restarts the reveal ceiling, so
     * the retried sections reveal together again. The progress summary is not part of Retry. The Level card
     * is retried whenever it is not [Content][LevelCardContent], with no ceiling of its own; a card that has
     * content is left alone.
     */
    fun onRetry() {
        val current = _state.value
        val retriesFavorites = current.favorites is FavoritesFailed
        val retriesRecents = current.recents is RecentsFailed
        val retriesLevelCard = current.levelCard !is LevelCardContent
        if (retriesFavorites || retriesRecents) {
            _state.update {
                it.copy(
                    favorites = if (retriesFavorites) FavoritesLoading else it.favorites,
                    recents = if (retriesRecents) RecentsLoading else it.recents,
                )
            }
            startRevealCeiling()
            if (retriesFavorites) collectFavoriteItems()
            if (retriesRecents) collectRecentSessions()
        }
        if (retriesLevelCard) {
            _state.update { it.copy(levelCard = LevelCardLoading) }
            collectLevelCard()
        }
    }

    /**
     * Hides the Favorites hint at once, then persists it fire-and-forget. A failed write only means the hint
     * comes back on a later launch. There is no way to show it again.
     */
    fun onFavoritesHintHide() {
        _state.update { it.copy(hasHiddenFavoritesHint = true) }
        viewModelScope.launch {
            saveUserPreference(HasHiddenFavoritesHint(true))
                .onFailure { error -> logw(error) { "Failed to persist the hidden Favorites hint" } }
        }
    }

    /** One ceiling for both sections, so a hung read cannot hold back [HomeScreenState.body] forever. */
    private fun startRevealCeiling() {
        revealCeilingJob?.cancel()
        _state.update { it.copy(hasRevealCeilingElapsed = false) }
        revealCeilingJob = viewModelScope.launch {
            delay(REVEAL_CEILING)
            _state.update { it.copy(hasRevealCeilingElapsed = true) }
        }
    }

    /**
     * The card needs the Level and the auth user together, so it stays [LevelCardLoading] until both have
     * emitted; a null auth user still gives [LevelCardContent], with no name or photo. The Level stream's end is
     * handled on that stream itself, not on the combined flow: the auth stream never ends, so `combine` would
     * wait on it forever. Before the Level's first emission a failure or a completion (a signed-out start ends
     * the stream silently) gives [LevelCardUnavailable]; after one, the card keeps waiting for the auth user or
     * keeps its last content, and auth changes still update it in place.
     */
    private fun collectLevelCard() {
        levelCardJob?.cancel()
        levelCardJob = viewModelScope.launch {
            var hasLevelEmitted = false
            val levelProgressFlow = flow { emitAll(observeLevelProgress()) }
                .onEach { hasLevelEmitted = true }
                .onCompletion { cause ->
                    if (cause == null && !hasLevelEmitted) failLevelCardIfLoading { "Observing the Level completed before its first emission" }
                }
                .catch { error ->
                    loge(error) { "Observing the Level failed" }
                    if (!hasLevelEmitted) failLevelCardIfLoading()
                }
            val authUserFlow = flow { emitAll(observeAuthUser()) }
            combine(levelProgressFlow, authUserFlow) { levelProgress, authUser -> levelProgress to authUser }
                .catch { error ->
                    loge(error) { "Observing the auth user for the Level card failed" }
                    failLevelCardIfLoading()
                }
                .collect { (levelProgress, authUser) ->
                    _state.update {
                        it.copy(levelCard = LevelCardContent(levelProgress, authUser?.photoUrl, authUser?.displayName))
                    }
                }
        }
    }

    private fun failLevelCardIfLoading(logMessage: (() -> String)? = null) {
        if (_state.value.levelCard !is LevelCardLoading) return
        logMessage?.let { message -> logw(message = message) }
        _state.update { it.copy(levelCard = LevelCardUnavailable) }
    }

    /**
     * Both observed flows are live Firestore listeners that retry only a permission-denied error, so any other
     * failure is caught here instead of crashing out of [viewModelScope]. The use case is called inside the flow
     * so a failure building it is caught too. Before the first emission, a failure, an [Unresolved] emission or
     * a completion (a permission-denied teardown ends the flow silently) gives [FavoritesFailed]; after one, the
     * last state stays. A later [Resolved] recovers from [Unresolved]; a thrown failure or a completion leaves
     * the flow dead until [onRetry] relaunches it.
     */
    private fun collectFavoriteItems() {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            flow { emitAll(observeFavoriteItems()) }
                .onCompletion { cause -> if (cause == null) failFavoritesIfLoading { "Observing Favorites completed before its first emission" } }
                .catch { error ->
                    loge(error) { "Observing Favorites failed" }
                    failFavoritesIfLoading()
                }
                .collect { result ->
                    when (result) {
                        is Resolved -> _state.update { current ->
                            current.copy(favorites = if (result.items.isEmpty()) FavoritesEmpty else FavoritesContent(result.items))
                        }

                        Unresolved -> failFavoritesIfLoading { "Favorites could not be fully fetched" }
                    }
                }
        }
    }

    private fun failFavoritesIfLoading(logMessage: (() -> String)? = null) {
        if (_state.value.favorites !is FavoritesLoading) return
        logMessage?.let { message -> logw(message = message) }
        _state.update { it.copy(favorites = FavoritesFailed) }
    }

    /**
     * Runs independently of [collectFavoriteItems] so a slow or failing progress read never gates the
     * cards. A failure leaves the resolved flag as it was: Subcategory cards keep their unresolved state.
     */
    private fun collectProgressSummary() {
        viewModelScope.launch {
            flow { emitAll(observeProgressSummary()) }
                .catch { error -> loge(error) { "Observing the progress summary failed" } }
                .collect { summary ->
                    _state.update { it.copy(progressSummary = summary, isProgressResolved = true) }
                }
        }
    }

    /**
     * Runs independently of [collectFavoriteItems] and [collectProgressSummary], with the same rules as
     * Favorites: before the first emission a failure or a completion gives [RecentsFailed], after one the last
     * state stays. The use case never drops a Recent, so it has no unresolved emission.
     */
    private fun collectRecentSessions() {
        recentsJob?.cancel()
        recentsJob = viewModelScope.launch {
            flow { emitAll(observeRecentSessions()) }
                .onCompletion { cause -> if (cause == null) failRecentsIfLoading { "Observing Recents completed before its first emission" } }
                .catch { error ->
                    loge(error) { "Observing Recents failed" }
                    failRecentsIfLoading()
                }
                .collect { recentItems ->
                    _state.update { current ->
                        current.copy(recents = if (recentItems.isEmpty()) RecentsEmpty else RecentsContent(recentItems))
                    }
                }
        }
    }

    /**
     * A read that fails or ends without a value counts as not hidden, so the body never waits on it. Hiding is
     * one-way, so a hide already made in memory stays even if a later emission still reads `false` because its
     * write failed.
     */
    private fun collectFavoritesHintPreference() {
        viewModelScope.launch {
            flow { emitAll(observeUserPreferences()) }
                .catch { error -> loge(error) { "Observing the Favorites hint preference failed" } }
                .collect { preferences ->
                    _state.update {
                        it.copy(hasHiddenFavoritesHint = it.hasHiddenFavoritesHint == true || preferences.hasHiddenFavoritesHint)
                    }
                }
            _state.update { it.copy(hasHiddenFavoritesHint = it.hasHiddenFavoritesHint ?: false) }
        }
    }

    private fun failRecentsIfLoading(logMessage: (() -> String)? = null) {
        if (_state.value.recents !is RecentsLoading) return
        logMessage?.let { message -> logw(message = message) }
        _state.update { it.copy(recents = RecentsFailed) }
    }

    internal companion object {
        /** How long Favorites and Recents wait for each other before a still-loading one is left out. */
        val REVEAL_CEILING = 2.seconds
    }
}
