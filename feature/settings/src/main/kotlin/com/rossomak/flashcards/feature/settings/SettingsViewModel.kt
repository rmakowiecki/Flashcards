package com.rossomak.flashcards.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.DefaultStudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.PartialRatingCardRequeueingEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.RatedAttempts
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.ReadAloudEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SessionLength
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SubcategoryCountRange as SubcategoryCountRangePreference
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoiceAnsweringEnabled
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SignOutUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.feature.settings.SettingsDialog.DailyStudyGoal
import com.rossomak.flashcards.feature.settings.SettingsDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.settings.SettingsDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardCount
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionMode
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.settings.SettingsDialog.SignOut
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val observeStudySessionPreferences: ObserveStudySessionPreferencesUseCase,
    private val saveUserPreference: SaveUserPreferenceUseCase,
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val voiceSettingsController: VoiceSettingsController,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsScreenState())
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<SettingsDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        observeUserPreferences()
            .onEach { preferences -> _state.update { it.copy(dailyGoalMinutes = preferences.dailyGoalMinutes) } }
            .launchIn(viewModelScope)
        observeStudySessionPreferences()
            .onEach { preferences ->
                _state.update {
                    it.copy(
                        sessionLength = preferences.sessionLength,
                        ratedAttempts = preferences.ratedAttempts,
                        partialRatingCardRequeueingEnabled = preferences.partialRatingCardRequeueingEnabled,
                        defaultStudyMode = preferences.defaultStudyMode,
                        sortOrder = preferences.sortOrder,
                        subcategoryCountRange = preferences.subcategoryCountRange,
                        voiceAnsweringEnabled = preferences.voiceAnsweringEnabled,
                        readAloudEnabled = preferences.readAloudEnabled,
                        speechRate = preferences.voiceSettings.speechRate,
                        voiceId = preferences.voiceSettings.voiceId,
                    )
                }
            }
            .launchIn(viewModelScope)
        // The row shows the voice's name, not its id, so the list is needed before the dialog is
        // ever opened. Cached afterwards, so opening the dialog costs no second platform query.
        voiceSettingsController.loadVoices(viewModelScope, ::onVoicesLoaded)
    }

    /** Single entry point for every dialog on this screen. */
    fun onDialogEvent(event: SettingsDialogEvent) {
        when (event) {
            is Open -> onDialogOpen(event.dialog)
            is DraftChange -> onDraftChange(event.dialog)
            Confirm -> onDialogConfirm()
            Dismiss -> onDialogDismiss()
        }
    }

    /**
     * The caller hands over the dialog it wants shown, already seeded from the row it sits under.
     * Voice settings is the one exception — its draft comes from the controller, not from screen
     * state — so the ViewModel replaces the placeholder it is handed and kicks off the load.
     */
    private fun onDialogOpen(dialog: SettingsDialog) {
        when (dialog) {
            is SessionVoiceSettings -> onVoiceSettingsOpen()
            else -> _state.update { it.copy(activeDialog = dialog) }
        }
    }

    private fun onVoiceSettingsOpen() {
        val current = _state.value
        val draft = voiceSettingsController.seedDraft(
            SavedVoiceSettings(speechRate = current.speechRate, voiceId = current.voiceId),
        )
        _state.update { it.copy(activeDialog = SessionVoiceSettings(draft)) }
        voiceSettingsController.loadVoices(viewModelScope, ::onVoicesLoaded)
    }

    /**
     * The voice list feeds two things: the row's summary, which needs it to turn the saved id into
     * a name, and an open voice dialog, which has to be found to be filled in — the one narrowing
     * cast left in the dialog path, once per load rather than once per edit. A dismissal in the
     * meantime correctly drops the dialog half.
     */
    private fun onVoicesLoaded(voices: List<VoiceOption>) {
        _state.update { state ->
            val withVoices = state.copy(availableVoices = voices)
            val dialog = withVoices.activeDialog as? SessionVoiceSettings ?: return@update withVoices
            withVoices.copy(
                activeDialog = dialog.copy(
                    draftState = dialog.draftState.copy(
                        availableVoices = voices,
                        draftVoiceId = dialog.draftState.draftVoiceId ?: voices.firstOrNull()?.id,
                    ),
                ),
            )
        }
    }

    /**
     * Stores the draft the host built, then previews the edit. The trigger is a diff rather than a
     * typed per-field event, so every dialog stays on the one generic
     * [SettingsDialogEvent.DraftChange] and the preview stays unit-testable (ADR-0036).
     */
    private fun onDraftChange(dialog: SettingsDialog) {
        val previous = _state.value.activeDialog
        _state.update { it.copy(activeDialog = dialog) }
        if (previous is SessionVoiceSettings &&
            dialog is SessionVoiceSettings &&
            dialog.draftState != previous.draftState
        ) {
            voiceSettingsController.preview(dialog.draftState)
        }
    }

    /**
     * Dismissal is the discard path: the draft dies with the field, so nothing is applied. Preview
     * playback is stopped only when it could have been started — every other dialog is silent, and
     * stopping the shared player from one of those could cut off audio this screen never began.
     */
    private fun onDialogDismiss() {
        if (_state.value.activeDialog is SessionVoiceSettings) {
            voiceSettingsController.stopPreview()
        }
        _state.update { it.copy(activeDialog = null) }
    }

    /**
     * The only commit path. Every dialog writes the sealed preference it edited and closes; the
     * row itself updates once the write lands back through [observeUserPreferences] /
     * [observeStudySessionPreferences] — no `copy()` into state here, so a row can never disagree
     * with disk. Sign-out is the one case with no draft, answering with an action instead
     * (ADR-0036).
     */
    private fun onDialogConfirm() {
        val dialog = _state.value.activeDialog ?: return

        // The two cases whose commit is an action rather than a field. Kept out of the write
        // below because `update` re-runs its lambda under contention, which would fire them twice.
        when (dialog) {
            is SignOut -> {
                _state.update { it.copy(activeDialog = null) }
                signOut()
                return
            }
            is SessionVoiceSettings -> {
                voiceSettingsController.save(viewModelScope, dialog.draftState)
                _state.update { it.copy(activeDialog = null) }
                return
            }
            else -> Unit
        }

        viewModelScope.launch {
            val result = saveDialogPreference(dialog)
            // The dialog closes either way — a dialog left open with a stale draft isn't a retry
            // path, it's a second write on the next confirm. The snackbar is the recovery signal.
            result.onFailure { _state.update { it.copy(saveError = "Failed to save setting") } }
        }
        _state.update { it.copy(activeDialog = null) }
    }

    /**
     * Lifted out of [onDialogConfirm] purely to keep that function under detekt's
     * `CyclomaticComplexMethod` threshold — [SignOut] and [SessionVoiceSettings] never reach here, both
     * having already returned above.
     */
    private suspend fun saveDialogPreference(dialog: SettingsDialog): Result<Unit> = when (dialog) {
        is SessionCardCount -> saveStudySessionPreference(SessionLength(dialog.draftState))
        is RatedSessionMaxCardAttempts -> saveStudySessionPreference(RatedAttempts(dialog.draftState))
        is RatedSessionPartialRatingCardRequeueing ->
            saveStudySessionPreference(PartialRatingCardRequeueingEnabled(dialog.draftState))
        is SessionMode -> saveStudySessionPreference(DefaultStudyMode(dialog.draftState))
        is SessionCardsSortingOrder -> saveStudySessionPreference(SortOrder(dialog.draftState))
        is QuickSessionSubcategoryCountRange -> saveStudySessionPreference(SubcategoryCountRangePreference(dialog.draftState))
        is RatedSessionVoiceAnswering -> saveStudySessionPreference(VoiceAnsweringEnabled(dialog.draftState))
        is FastSessionReadAloud -> saveStudySessionPreference(ReadAloudEnabled(dialog.draftState))
        is DailyStudyGoal -> saveUserPreference(DailyGoalMinutes(dialog.draftState))
        // Both returned above; repeated only because the `when` is exhaustive.
        is SessionVoiceSettings, SignOut -> Result.success(Unit)
    }

    fun onSaveErrorDismissed() {
        _state.update { it.copy(saveError = null) }
    }

    private fun signOut() {
        if (_state.value.isSigningOut) {
            return
        }

        _state.update { it.copy(isSigningOut = true) }

        viewModelScope.launch {
            try {
                signOutUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Intentionally navigate to login even if remote sign-out fails.
            } finally {
                _state.update { it.copy(isSigningOut = false) }
                eventChannel.send(SettingsDestination.Login)
            }
        }
    }
}
