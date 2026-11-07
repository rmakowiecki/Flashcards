package com.rossomak.flashcards.debugtools

import android.os.Process
import android.system.OsConstants
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val getAppVersion: GetAppVersionUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugScreenState())
    val state: StateFlow<DebugScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<DebugDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val appVersion = getAppVersion()
            _state.update { it.copy(appVersion = appVersion) }
        }
    }

    /**
     * Clears the completion flag before navigating, so the flow behaves exactly as it does for a
     * first-run user — including committing preferences again on its final step — rather than
     * being a read-only walkthrough that behaves differently from the real thing.
     */
    fun onReplayOnboardingClick() {
        viewModelScope.launch {
            saveUserPreference(HasSeenOnboarding(false))
                .onSuccess { eventChannel.send(DebugDestination.Onboarding) }
                .onFailure { loge(it) { "Failed to clear onboarding flag for replay" } }
        }
    }

    fun onCrashNowClick(): Nothing = throw DebugCrashException("Crash triggered from the debug hub")

    /** Raises SIGABRT, which bypasses the JVM's handler, so only the Crashlytics NDK reports it. */
    fun onNativeCrashClick() {
        Process.sendSignal(Process.myPid(), OsConstants.SIGABRT)
    }

    /** Goes through `AppLog`, the same path a real caught failure takes. */
    fun onRecordNonFatalClick() {
        loge(DebugCrashException("Non-fatal triggered from the debug hub")) { "Debug hub non-fatal" }
    }
}

/** Tells a deliberate debug-hub report apart from a real failure. */
private class DebugCrashException(message: String) : RuntimeException(message)
