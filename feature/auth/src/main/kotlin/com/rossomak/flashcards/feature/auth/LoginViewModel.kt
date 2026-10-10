package com.rossomak.flashcards.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.usecase.CheckInternetAvailabilityUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import com.rossomak.flashcards.feature.auth.LoginDestination.Main
import com.rossomak.flashcards.feature.auth.LoginDestination.Onboarding
import com.rossomak.flashcards.feature.auth.LoginMessage.SignInFailed
import com.rossomak.flashcards.feature.auth.LoginPhase.Idle
import com.rossomak.flashcards.feature.auth.LoginPhase.SignedIn
import com.rossomak.flashcards.feature.auth.LoginPhase.SigningIn
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
class LoginViewModel @Inject constructor(
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val checkInternetAvailability: CheckInternetAvailabilityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginScreenState())
    val state: StateFlow<LoginScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<LoginDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<LoginMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<LoginMessage> = _messages.asSharedFlow()

    fun onGoogleSignInStarted() {
        _state.update { it.copy(phase = SigningIn) }
    }

    /** The account picker's result: an ID token, or the raw throwable it failed or was dismissed with. */
    fun onGoogleSignInResult(idTokenResult: Result<String>) {
        idTokenResult
            .onSuccess { idToken -> signInWithIdToken(idToken) }
            .onFailure { error -> handleSignInFailure(error) }
    }

    /**
     * The account picker's coroutine was cancelled before it returned, because the Activity was
     * recreated while it was open. Not a failure: this only stops the screen staying "Signing in…".
     */
    fun onGoogleSignInInterrupted() {
        _state.update { it.copy(phase = Idle) }
    }

    private fun signInWithIdToken(idToken: String) {
        viewModelScope.launch {
            signInWithGoogle(idToken)
                .onSuccess {
                    // Before the preferences read, so the button never comes back while the screen leaves.
                    _state.update { it.copy(phase = SignedIn) }
                    // The onboarding flag is device-scoped, so a second account signing in on a
                    // device that has already been through the flow goes straight to Main.
                    val hasSeenOnboarding = observeUserPreferences().first().hasSeenOnboarding
                    eventChannel.send(if (hasSeenOnboarding) Main else Onboarding)
                }
                .onFailure { error -> handleSignInFailure(error) }
        }
    }

    /** Clears the flag first, so the button is enabled again by the time the snackbar appears. */
    private fun handleSignInFailure(error: Throwable) {
        _state.update { it.copy(phase = Idle) }
        if (error.isGoogleSignInCancellation()) return
        viewModelScope.launch {
            // Read after the failure, not before the picker opens, so a wrong "offline" reading can
            // never block the picker.
            val isInternetAvailable = checkInternetAvailability()
            loge(error) { "Sign-in failed, internet available: $isInternetAvailable" }
            _messages.tryEmit(SignInFailed(error.toLoginFailureReason(isInternetAvailable)))
        }
    }
}
