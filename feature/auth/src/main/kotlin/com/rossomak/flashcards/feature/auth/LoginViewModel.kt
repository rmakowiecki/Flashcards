package com.rossomak.flashcards.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginScreenState())
    val state: StateFlow<LoginScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<LoginDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun onSignInStarted() {
        _state.update { it.copy(isSigningIn = true, failureReason = null) }
    }

    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            signInWithGoogleUseCase(idToken)
                .onSuccess {
                    _state.update { it.copy(isSigningIn = false, failureReason = null) }
                    // The onboarding flag is device-scoped, so a second account signing in on a
                    // device that has already been through the flow goes straight to Main.
                    val hasSeenOnboarding = observeUserPreferences().first().hasSeenOnboarding
                    eventChannel.send(
                        if (hasSeenOnboarding) LoginDestination.Main else LoginDestination.Onboarding,
                    )
                }
                .onFailure { error ->
                    loge(error) { "Sign-in failed" }
                    _state.update {
                        it.copy(isSigningIn = false, failureReason = LoginFailureReason.SignInFailed(error))
                    }
                }
        }
    }

    fun onSignInCancelled() {
        _state.update { it.copy(isSigningIn = false, failureReason = null) }
    }

    fun onSignInFailed(reason: LoginFailureReason) {
        _state.update { it.copy(isSigningIn = false, failureReason = reason) }
    }
}
