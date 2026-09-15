package com.rossomak.flashcards.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncFlashcardCacheGenerationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val syncFlashcardCacheGeneration: SyncFlashcardCacheGenerationUseCase,
) : ViewModel() {

    val startupState: StateFlow<AppStartupState> =
        flow {
            // Anonymous sessions don't count as authenticated here either, for the same reason as
            // SplashViewModel (see docs/temp/onboarding-before-login-spec.md) — kept consistent
            // even though this field only gates the system splash screen today, not routing.
            val authenticated = withTimeoutOrNull(STARTUP_AUTH_TIMEOUT_MS.milliseconds) {
                val authUser = getCurrentAuthUser()
                authUser != null && !authUser.isAnonymous
            } ?: false
            emit(AppStartupState.Ready(authenticated = authenticated))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppStartupState.Loading
        )

    init {
        // Decoupled from startupState on purpose (ADR-0039): this check has zero influence on
        // Ready's value, so it runs as its own side effect rather than racing the auth check
        // inside the same flow, where a future edit could accidentally make it gate startup.
        // No artificial timeout either — a cold-start Firestore SERVER read (TLS + gRPC channel
        // setup + auth token attach) routinely runs past the 800ms the auth check bounds itself
        // to, and unlike that check, nothing here is waiting on the result: viewModelScope already
        // cancels this coroutine when the ViewModel clears, which is bound enough.
        // No try/catch either — SyncFlashcardCacheGenerationUseCase owns "never throws" as its own
        // contract (best-effort by design), so this call site trusts it rather than duplicating
        // the guard.
        viewModelScope.launch {
            syncFlashcardCacheGeneration()
        }
    }

    private companion object {
        const val STARTUP_AUTH_TIMEOUT_MS = 1000L
    }
}
