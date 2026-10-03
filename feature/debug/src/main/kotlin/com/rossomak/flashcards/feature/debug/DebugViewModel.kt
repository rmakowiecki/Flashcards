package com.rossomak.flashcards.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val saveUserPreference: SaveUserPreferenceUseCase,
) : ViewModel() {

    private val eventChannel = Channel<DebugDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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
}
