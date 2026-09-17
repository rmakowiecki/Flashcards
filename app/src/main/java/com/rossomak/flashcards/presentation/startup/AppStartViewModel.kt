package com.rossomak.flashcards.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.usecase.ClearDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncFlashcardCacheGenerationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val observeAuthUser: ObserveAuthUserUseCase,
    private val syncFlashcardCacheGeneration: SyncFlashcardCacheGenerationUseCase,
    private val syncDynamicShortcuts: SyncDynamicShortcutsUseCase,
    private val clearDynamicShortcuts: ClearDynamicShortcutsUseCase,
) : ViewModel() {

    val startupState: StateFlow<AppStartupState> = flow {
        // Anonymous users (only transiently used in onboarding) don't count as authenticated
        // sign-in is mandatory, so an anon user must still be routed through Login
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
        viewModelScope.launch {
            syncFlashcardCacheGeneration()
        }
        viewModelScope.launch {
            // Dynamic shortcuts mirror per-user favorites, so this tracks live auth changes rather than startupState's one-shot check
            // a sign-out mid-session must clear shortcuts from the previous session immediately, not just on the next cold start
            // collectLatest cancels syncDynamicShortcuts()'s never-completing favorites collection as soon as the uid flips, before the next branch runs.
            observeAuthUser()
                .map { authUser -> authUser?.takeUnless { it.isAnonymous }?.uid }
                .distinctUntilChanged()
                .collectLatest { authenticatedUid ->
                    if (authenticatedUid != null) {
                        syncDynamicShortcuts()
                    } else {
                        clearDynamicShortcuts()
                    }
                }
        }
    }

    private companion object {
        const val STARTUP_AUTH_TIMEOUT_MS = 1000L
    }
}
