package com.rossomak.flashcards.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
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
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
) : ViewModel() {

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
            _hasSeenOnboarding.value = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS.milliseconds) {
                observeUserPreferences().first().hasSeenOnboarding
            } ?: false
        }
        viewModelScope.launch {
            val destination = combine(
                _animationCompleted,
                _authenticated,
                _hasSeenOnboarding,
            ) { animationCompleted, authenticated, hasSeenOnboarding ->
                if (!animationCompleted || authenticated == null || hasSeenOnboarding == null) {
                    null
                } else {
                    // Onboarding-before-login (docs/temp/onboarding-before-login-spec.md): a
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
            eventChannel.send(destination)
        }
    }

    fun onAnimationCompleted() {
        _animationCompleted.value = true
    }

    private companion object {
        const val AUTH_TIMEOUT_MS = 1000L
        const val PREFERENCES_TIMEOUT_MS = 1000L
    }
}
