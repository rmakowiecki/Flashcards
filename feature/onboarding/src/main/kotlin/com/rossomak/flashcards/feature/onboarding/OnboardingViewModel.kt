package com.rossomak.flashcards.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.GetOnboardingSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObservePermissionStatusUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveVoiceDemoInputLevelsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveVoiceDemoStateUseCase
import com.rossomak.flashcards.core.domain.usecase.PlayVoiceDemoUseCase
import com.rossomak.flashcards.core.domain.usecase.RequestPermissionUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveOnboardingPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SetFavoriteSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInAnonymouslyUseCase
import com.rossomak.flashcards.core.domain.usecase.StartVoiceDemoUseCase
import com.rossomak.flashcards.core.domain.usecase.StopVoiceDemoUseCase
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.feature.onboarding.model.FavoriteSubcategoryOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
@Suppress("LongParameterList") // one UseCase per collaborator; a holder class would only rename the sprawl.
class OnboardingViewModel @Inject constructor(
    private val getCurrentAuthUser: GetCurrentAuthUserUseCase,
    private val saveOnboardingPreferences: SaveOnboardingPreferencesUseCase,
    private val getOnboardingSubcategories: GetOnboardingSubcategoriesUseCase,
    private val setFavoriteSubcategories: SetFavoriteSubcategoriesUseCase,
    private val signInAnonymously: SignInAnonymouslyUseCase,
    private val observeVoiceDemoState: ObserveVoiceDemoStateUseCase,
    private val observeVoiceDemoInputLevels: ObserveVoiceDemoInputLevelsUseCase,
    private val startVoiceDemo: StartVoiceDemoUseCase,
    private val playVoiceDemo: PlayVoiceDemoUseCase,
    private val stopVoiceDemo: StopVoiceDemoUseCase,
    private val observePermissionStatus: ObservePermissionStatusUseCase,
    private val requestPermission: RequestPermissionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingScreenState())
    val state: StateFlow<OnboardingScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<OnboardingDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<OnboardingMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<OnboardingMessage> = _messages.asSharedFlow()

    /** Voice test microphone levels. Kept out of [state] so each update recomposes only the indicator. */
    val voiceDemoInputLevels: StateFlow<ImmutableList<Float>> =
        flow { emitAll(observeVoiceDemoInputLevels()) }
            .map { levels -> levels.toImmutableList() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = INPUT_LEVELS_STOP_TIMEOUT.inWholeMilliseconds),
                initialValue = RestInputLevels,
            )

    private var permissionStatusJob: Job? = null

    /** Guards "Test your voice" against a second tap while the microphone request is still pending. */
    private var micRequestInFlight = false

    /**
     * Set by [onVoiceDemoStop] so a request still pending when the user leaves the step (or the app
     * stops) settles without effect. The request itself is never cancelled: the permission layer
     * must still see the answer to record a permanent refusal. Should a device stop the app for the
     * system prompt itself, a grant then needs one more tap, which fails safe.
     */
    private var micRequestStopped = false

    init {
        viewModelScope.launch {
            val authUser = getCurrentAuthUser()
            val userName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: authUser?.email
            _state.update { it.copy(userName = userName) }
        }
        viewModelScope.launch {
            observeVoiceDemoState().collect { voiceDemoState ->
                _state.update { it.copy(voiceDemoState = voiceDemoState) }
                if (voiceDemoState is VoiceDemoState.Failed) {
                    _messages.tryEmit(OnboardingMessage.VoiceDemoFailed(voiceDemoState.reason))
                }
            }
        }
    }

    /**
     * Restarts the microphone status collection. The status flow is cold and never polls, so each
     * resume — first composition, return from system Settings, or the end of a system prompt — is
     * what picks up a change made outside the app.
     */
    fun onResume() {
        permissionStatusJob?.cancel()
        permissionStatusJob = viewModelScope.launch {
            observePermissionStatus(AppPermission.RecordAudio).collect { status ->
                _state.update { it.copy(micPermissionStatus = status) }
            }
        }
    }

    /**
     * "Test your voice" and every Retry route here. The microphone is requested through the shared
     * permission layer first, which returns without prompting when it is already granted; only a
     * grant starts the demo.
     *
     * The result only steers this tap; [OnboardingScreenState.micPermissionStatus] is written solely
     * by the observed status. A refusal that was already permanent before this tap shows no prompt
     * at all, so it gets a snackbar instead of a silent no-op.
     */
    fun onVoiceDemoStart() {
        if (micRequestInFlight) return
        micRequestInFlight = true
        micRequestStopped = false
        viewModelScope.launch {
            try {
                val statusBeforeRequest = _state.value.micPermissionStatus
                val status = requestPermission(AppPermission.RecordAudio)
                when {
                    micRequestStopped -> Unit
                    status == PermissionStatus.Granted -> startVoiceDemo()
                    statusBeforeRequest == PermissionStatus.PermanentlyDenied && status == PermissionStatus.PermanentlyDenied ->
                        _messages.tryEmit(OnboardingMessage.MicPermissionStillDenied)
                }
            } finally {
                micRequestInFlight = false
            }
        }
    }

    fun onVoiceDemoPlay() {
        viewModelScope.launch { playVoiceDemo() }
    }

    /** Hard-stops the demo: pager navigation away from the step, or the app backgrounding. */
    fun onVoiceDemoStop() {
        micRequestStopped = true
        viewModelScope.launch { stopVoiceDemo() }
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
     * Onboarding runs before Login (see docs/design/onboarding-flow.md), so finishing
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
                    if (getCurrentAuthUser() == null) {
                        withOnboardingTimeout(SIGN_IN_ANONYMOUSLY_TIMEOUT_MS) { signInAnonymously() }.getOrThrow()
                    }
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
        val INPUT_LEVELS_STOP_TIMEOUT = 5.seconds
        val RestInputLevels: ImmutableList<Float> =
            List(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT) { 0f }.toImmutableList()
    }
}
