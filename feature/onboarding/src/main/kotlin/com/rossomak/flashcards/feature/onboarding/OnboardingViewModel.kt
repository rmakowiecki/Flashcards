package com.rossomak.flashcards.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveOnboardingPreferencesUseCase
import com.rossomak.flashcards.feature.onboarding.model.FavoriteTopicOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val saveOnboardingPreferences: SaveOnboardingPreferencesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingScreenState(favoriteTopicOptions = SAMPLE_FAVORITE_TOPIC_OPTIONS),
    )
    val state: StateFlow<OnboardingScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<OnboardingDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val authUser = getCurrentAuthUser()
            val userName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: authUser?.email
            _state.update { it.copy(userName = userName) }
        }
    }

    fun onStudyModeSelect(studyMode: StudyMode) {
        _state.update { it.copy(defaultStudyMode = studyMode) }
    }

    fun onDailyGoalDecrement() {
        _state.update { it.copy(dailyGoalMinutes = DailyGoal.coerce(it.dailyGoalMinutes - DailyGoal.STEP_MINUTES)) }
    }

    fun onDailyGoalIncrement() {
        _state.update { it.copy(dailyGoalMinutes = DailyGoal.coerce(it.dailyGoalMinutes + DailyGoal.STEP_MINUTES)) }
    }

    fun onFavoriteTopicToggle(topicId: String) {
        _state.update { current ->
            val selected = current.selectedFavoriteTopicIds
            val updated = if (topicId in selected) selected - topicId else selected + topicId
            current.copy(selectedFavoriteTopicIds = updated.toPersistentSet())
        }
    }

    /**
     * The flow's only exit. Reaching it via Skip and reaching it by walking every step are the same
     * call — Skip jumps to the final page rather than leaving, so there is exactly one place where
     * preferences are written.
     *
     * The completion flag is flipped only after the preferences write succeeds, so a failed write
     * leaves the flow pending rather than silently losing the user's choices. Navigation happens
     * either way: nothing the user can do from this screen would fix a local storage failure, and
     * trapping them on the last page of onboarding is worse than re-showing the flow next launch.
     *
     * Onboarding now runs before Login (see docs/temp/onboarding-before-login-spec.md), so finishing
     * it does not guarantee a signed-in user: an anonymous session does not count, since sign-in is
     * mandatory, never a standing alternative to it. Main is reachable only for an already-real
     * signed-in user (e.g. Replay onboarding on an authenticated device); everyone else is sent to
     * Login, which already routes back to Main afterwards since `hasSeenOnboarding` is now true.
     */
    fun onFinish() {
        if (_state.value.isCommitting) {
            return
        }
        _state.update { it.copy(isCommitting = true) }

        viewModelScope.launch {
            val params = SaveOnboardingPreferencesUseCase.Params(
                defaultStudyMode = _state.value.defaultStudyMode,
                dailyGoalMinutes = _state.value.dailyGoalMinutes,
            )
            // TODO(favorites): persist state.selectedFavoriteTopicIds to users/{uid}/favorites here.
            //  Firestore's offline persistence resolves a write locally but leaves the returned
            //  Task pending until the server acks, so that call must not be awaited unbounded — a
            //  withTimeout, or no await at all, otherwise an offline user hangs on this screen.
            saveOnboardingPreferences(params)
            val authUser = getCurrentAuthUser()
            val authenticated = authUser != null && !authUser.isAnonymous
            _state.update { it.copy(isCommitting = false) }
            eventChannel.send(if (authenticated) OnboardingDestination.Main else OnboardingDestination.Login)
        }
    }

    private companion object {
        /**
         * Stand-in for the Favorites step's topic grid. Real Subcategories are not fetched yet —
         * favourites are not persisted in this release, so querying them would add loading and
         * error states to a screen whose selections currently go nowhere.
         */
        // TODO(favorites): replace with a real featured-subcategory query once favourites persist.
        val SAMPLE_FAVORITE_TOPIC_OPTIONS = persistentListOf(
            FavoriteTopicOption(id = "compose", name = "Compose", categoryName = "Android"),
            FavoriteTopicOption(id = "coroutines", name = "Coroutines", categoryName = "Kotlin"),
            FavoriteTopicOption(id = "compose-navigation", name = "Compose Navigation", categoryName = "Android"),
            FavoriteTopicOption(id = "async", name = "Async", categoryName = "Python"),
            FavoriteTopicOption(id = "typing", name = "Typing", categoryName = "Python"),
            FavoriteTopicOption(id = "standard-library", name = "Standard Library", categoryName = "Python"),
            FavoriteTopicOption(id = "swiftui", name = "SwiftUI", categoryName = "iOS"),
            FavoriteTopicOption(id = "combine", name = "Combine", categoryName = "iOS"),
            FavoriteTopicOption(id = "collections", name = "Collections", categoryName = "Kotlin"),
            FavoriteTopicOption(id = "workmanager", name = "WorkManager", categoryName = "Android"),
        )
    }
}
