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

    val startupState: StateFlow<AppStartupState> = flow {
        // Anonymous sessions don't count: sign-in is mandatory, so an anonymous Firebase user must still be routed through Login
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
    }

    private companion object {
        const val STARTUP_AUTH_TIMEOUT_MS = 1000L
    }
}
