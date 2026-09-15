package com.rossomak.flashcards.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.GetOnboardingSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveOnboardingPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SetFavoriteSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInAnonymouslyUseCase
import com.rossomak.flashcards.feature.onboarding.model.FavoriteSubcategoryOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val saveOnboardingPreferences: SaveOnboardingPreferencesUseCase,
    private val getOnboardingSubcategories: GetOnboardingSubcategoriesUseCase,
    private val setFavoriteSubcategories: SetFavoriteSubcategoriesUseCase,
    private val signInAnonymously: SignInAnonymouslyUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingScreenState())
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

    fun onFavoriteSubcategoryToggle(subcategoryId: String) {
        _state.update { current ->
            val selected = current.selectedFavoriteSubcategoriesIds
            val updated = if (subcategoryId in selected) selected - subcategoryId else selected + subcategoryId
            current.copy(selectedFavoriteSubcategoriesIds = updated.toPersistentSet())
        }
    }

    /**
     * Fires the curated Favorites list fetch the first time the step is actually reached — never
     * prefetched in [init], since the list is meaningless until the user gets there. A no-op on any
     * later re-entry (e.g. swiping back and forth): once a load has settled, success or failure,
     * only [onFavoriteSubcategoriesRetry] fires another attempt.
     */
    fun onFavoritesStepEntered() {
        val current = _state.value
        if (current.isFavoriteSubcategoriesLoading || current.favoriteSubcategoriesLoaded || current.favoriteSubcategoriesLoadingFailed) {
            return
        }
        loadFavoriteSubcategoryOptions()
    }

    fun onFavoriteSubcategoriesRetry() {
        loadFavoriteSubcategoryOptions()
    }

    private fun loadFavoriteSubcategoryOptions() {
        _state.update { it.copy(isFavoriteSubcategoriesLoading = true, favoriteSubcategoriesLoadingFailed = false) }
        viewModelScope.launch {
            getOnboardingSubcategories()
                .onSuccess { subcategories ->
                    val options = subcategories.map { subcategory ->
                        FavoriteSubcategoryOption(
                            id = subcategory.id,
                            name = subcategory.name,
                            categoryName = subcategory.categoryName,
                            iconSvg = subcategory.iconSvg,
                        )
                    }.toPersistentList()
                    _state.update {
                        it.copy(
                            favoriteSubcategoryOptions = options,
                            isFavoriteSubcategoriesLoading = false,
                            favoriteSubcategoriesLoaded = true,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isFavoriteSubcategoriesLoading = false, favoriteSubcategoriesLoadingFailed = true) }
                }
        }
    }

    /**
     * The flow's only exit. Reaching it via Skip and reaching it by walking every step are the same
     * call — Skip jumps to the final page rather than leaving, so there is exactly one place where
     * preferences are written.
     *
     * When at least one favorite was picked, a transient anonymous Firebase session (the **Guest**
     * entity, CONTEXT.md) is started and the picks are written under its uid *before* preferences
     * are saved — each step gates the next via `Result.getOrThrow()`, same chained style
     * [SaveOnboardingPreferencesUseCase] itself uses, so a failure anywhere in that lead-in leaves
     * `hasSeenOnboarding` unset exactly like a failure inside [saveOnboardingPreferences] already
     * does. Skip (or picking nothing) never starts a Guest session at all — `favoriteCount == 0`
     * skips the whole lead-in. Both the sign-in and the favorites write are bounded by
     * [withTimeoutOrNull]: Firestore's offline persistence resolves a write locally but leaves the
     * returned Task pending until the server acks, so an unbounded await would hang an offline user
     * on this screen; a timeout here counts as failure for the gate above.
     *
     * The completion flag is flipped only after every write above succeeds, so a failed step leaves
     * the flow pending rather than silently losing the user's choices. Navigation happens either
     * way: nothing the user can do from this screen would fix a write failure, and trapping them on
     * the last page of onboarding is worse than re-showing the flow next launch.
     *
     * Onboarding now runs before Login (see docs/temp/onboarding-before-login-spec.md), so finishing
     * it does not guarantee a signed-in user: an anonymous session does not count, since sign-in is
     * mandatory, never a standing alternative to it. Main is reachable only for an already-real
     * signed-in user (e.g. Replay onboarding on an authenticated device); everyone else is sent to
     * Login, which already routes back to Main afterwards since `hasSeenOnboarding` is now true.
     */
    // Generic catch is deliberate: any failure anywhere in the lead-in chain above collapses to the
    // same "leave hasSeenOnboarding unset, navigate anyway" outcome, same as SaveOnboardingPreferencesUseCase's own boundary.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun onFinish() {
        if (_state.value.isCommitting) {
            return
        }
        _state.update { it.copy(isCommitting = true) }

        viewModelScope.launch {
            val selectedIds = _state.value.selectedFavoriteSubcategoriesIds
            try {
                if (selectedIds.isNotEmpty()) {
                    withOnboardingTimeout(SIGN_IN_ANONYMOUSLY_TIMEOUT_MS) { signInAnonymously() }.getOrThrow()
                    withOnboardingTimeout(SET_FAVORITES_TIMEOUT_MS) {
                        setFavoriteSubcategories(SetFavoriteSubcategoriesUseCase.Params(selectedIds))
                    }.getOrThrow()
                }
                val params = SaveOnboardingPreferencesUseCase.Params(
                    defaultStudyMode = _state.value.defaultStudyMode,
                    dailyGoalMinutes = _state.value.dailyGoalMinutes,
                )
                saveOnboardingPreferences(params).getOrThrow()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Swallowed deliberately: hasSeenOnboarding stays unset, so the flow replays next
                // launch (see this function's doc comment) rather than trapping the user here.
            }
            val authUser = getCurrentAuthUser()
            val authenticated = authUser != null && !authUser.isAnonymous
            _state.update { it.copy(isCommitting = false) }
            eventChannel.send(if (authenticated) OnboardingDestination.Main else OnboardingDestination.Login)
        }
    }

    private suspend fun <T> withOnboardingTimeout(timeoutMs: Long, block: suspend () -> Result<T>): Result<T> =
        withTimeoutOrNull(timeoutMs.milliseconds) { block() }
            ?: Result.failure(IllegalStateException("Onboarding commit step timed out"))

    private companion object {
        const val SIGN_IN_ANONYMOUSLY_TIMEOUT_MS = 8000L
        const val SET_FAVORITES_TIMEOUT_MS = 8000L
    }
}
