package com.rossomak.flashcards.presentation.splash

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.IsSignedInUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.ui.navigation.decodeRoute
import com.rossomak.flashcards.presentation.shortcut.ShortcutRouteResolver
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
    private val isSignedIn: IsSignedInUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val shortcutRouteResolver: ShortcutRouteResolver,
) : ViewModel() {

    /**
     * A launcher shortcut's route, null on an ordinary launch. Acted on only when Splash goes to Main;
     * before sign-in it is dropped, because Firestore denies unauthenticated reads (ADR-0003).
     */
    private val pendingRoute: String? = savedStateHandle.decodeRoute<Splash>().pendingRoute

    private val _animationCompleted = MutableStateFlow(false)
    private val _authenticated = MutableStateFlow<Boolean?>(null)
    private val _hasSeenOnboarding = MutableStateFlow<Boolean?>(null)

    private val eventChannel = Channel<SplashDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            _authenticated.value = isSignedIn()
        }
        viewModelScope.launch {
            val hasSeenValue = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS.milliseconds) {
                observeUserPreferences().first().hasSeenOnboarding
            } ?: true
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
                resolvePendingRoute(pendingRoute)
            } else {
                baseDestination
            }
            eventChannel.send(destination)
        }
    }

    fun onAnimationCompleted() {
        _animationCompleted.value = true
    }

    /** A route that does not resolve lands on Main as an ordinary launch, with no error. */
    private suspend fun resolvePendingRoute(route: String): SplashDestination =
        shortcutRouteResolver.resolve(route)?.let(SplashDestination::OpenShortcut) ?: SplashDestination.Main

    private companion object {
        const val PREFERENCES_TIMEOUT_MS = 1000L
    }
}
