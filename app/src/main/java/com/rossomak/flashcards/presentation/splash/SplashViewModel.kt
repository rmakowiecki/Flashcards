package com.rossomak.flashcards.presentation.splash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.ShortcutRoute
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import com.rossomak.flashcards.ui.navigation.Splash
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class SplashViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val flashcardRepository: FlashcardRepository,
) : ViewModel() {

    /**
     * Raw route string off a launcher-shortcut Intent (`AppShortcutsRepository.EXTRA_ROUTE`,
     * ADR-0003 shape: `/study/category/{id}` or `/study/category/{id}/subcategory/{id}`), null on
     * every ordinary launch. Only ever acted on for an already-authenticated, already-onboarded
     * device (see [resolvePendingRoute]) — an unauthenticated tap on a stale pinned shortcut just
     * lands on the normal Login screen with the route silently dropped, the same as if no shortcut
     * were involved. The alternative (carrying it through Login and resolving it after sign-in)
     * was deliberately rejected: Firestore denies unauthenticated reads of categories/subcategories
     * (`firestore.rules`), so nothing here could validate it before sign-in anyway, and the
     * resulting cross-screen hand-off wasn't worth it for what is, at worst, a launcher shortcut
     * pointing nowhere until the user re-pins it.
     */
    private val pendingRoute: String? = savedStateHandle.decodeRoute<Splash>().pendingRoute

    private val _animationCompleted = MutableStateFlow(false)
    private val _authenticated = MutableStateFlow<Boolean?>(null)
    private val _hasSeenOnboarding = MutableStateFlow<Boolean?>(null)

    private val eventChannel = Channel<SplashDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Anonymous sessions don't count: sign-in is mandatory, so an anonymous Firebase user must still be routed through Login
            val authenticated = withTimeoutOrNull(AUTH_TIMEOUT_MS.milliseconds) {
                val authUser = getCurrentAuthUser()
                authUser != null && !authUser.isAnonymous
            } ?: false
            _authenticated.value = authenticated
        }
        viewModelScope.launch {
            val hasSeenValue = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS.milliseconds) {
                observeUserPreferences().first().hasSeenOnboarding
            } ?: false
            _hasSeenOnboarding.value = hasSeenValue
        }
        viewModelScope.launch {
            val baseDestination = combine(
                _animationCompleted,
                _authenticated,
                _hasSeenOnboarding,
            ) { animationCompleted, authenticated, hasSeenOnboarding ->
                if (!animationCompleted || authenticated == null || hasSeenOnboarding == null) {
                    null
                } else {
                    // first-time device always sees Onboarding first, authenticated or not.
                    // Onboarding itself routes on to Login afterwards when still unauthenticated,
                    // so this check only needs to place returning devices correctly.
                    when {
                        !hasSeenOnboarding -> SplashDestination.Onboarding
                        !authenticated -> SplashDestination.Login
                        else -> SplashDestination.Main
                    }
                }
            }.filterNotNull().first()

            val destination = if (baseDestination == SplashDestination.Main && pendingRoute != null) {
                withTimeoutOrNull(RESOLVE_PENDING_ROUTE_TIMEOUT_MS.milliseconds) {
                    resolvePendingRoute(pendingRoute)
                } ?: SplashDestination.Main
            } else {
                baseDestination
            }
            eventChannel.send(destination)
        }
    }

    fun onAnimationCompleted() {
        _animationCompleted.value = true
    }

    /**
     * Null means [route] didn't parse or its id no longer resolves (stale shortcut, e.g. its
     * Category/Subcategory was deleted server-side) — caller falls back to the normal Browse
     * landing, silently, rather than surfacing an error for what is a launcher shortcut's problem,
     * not the user's. A server-side rename is not a stale case: routes are id-based, and the fetch
     * below always returns the current name.
     */
    private suspend fun resolvePendingRoute(route: String): SplashDestination? {
        val subcategoryMatch = ShortcutRoute.SUBCATEGORY_ROUTE_REGEX.matchEntire(route)
        if (subcategoryMatch != null) {
            return resolveSubcategoryRoute(
                categoryId = subcategoryMatch.groupValues[1],
                subcategoryId = subcategoryMatch.groupValues[2],
            )
        }
        val categoryMatch = ShortcutRoute.CATEGORY_ROUTE_REGEX.matchEntire(route)
        if (categoryMatch != null) {
            return resolveCategoryRoute(categoryMatch.groupValues[1])
        }
        return null
    }

    /** Rejects a route whose subcategory resolves under a different category than the route names — a malformed or stale-reorg route, not a valid one. */
    private suspend fun resolveSubcategoryRoute(categoryId: String, subcategoryId: String): SplashDestination? {
        val subcategory = flashcardRepository.fetchSubcategoriesByIds(setOf(subcategoryId))
            .getOrNull()
            ?.firstOrNull()
            ?.takeIf { it.categoryId == categoryId }
            ?: return null
        return SplashDestination.ToSubcategoryDetails(
            SubcategoryDetailsRoute(
                categoryId = subcategory.categoryId,
                categoryName = subcategory.categoryName,
                subcategoryId = subcategory.id,
                subcategoryName = subcategory.name,
            ),
        )
    }

    private suspend fun resolveCategoryRoute(categoryId: String): SplashDestination? {
        val category = flashcardRepository.fetchCategoriesByIds(setOf(categoryId))
            .getOrNull()
            ?.firstOrNull()
            ?: return null
        return SplashDestination.ToCategoryDetails(
            CategoryDetailsRoute(categoryId = category.id, categoryName = category.name),
        )
    }

    private companion object {
        const val AUTH_TIMEOUT_MS = 1000L
        const val PREFERENCES_TIMEOUT_MS = 1000L
        const val RESOLVE_PENDING_ROUTE_TIMEOUT_MS = 1000L
    }
}
