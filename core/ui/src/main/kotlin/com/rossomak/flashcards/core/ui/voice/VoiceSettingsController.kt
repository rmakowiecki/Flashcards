package com.rossomak.flashcards.core.ui.voice

import android.util.Log
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoicePlayback
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.voiceLabel
import com.rossomak.flashcards.core.domain.repository.VoicePreviewGateway
import com.rossomak.flashcards.core.domain.usecase.GetAvailableVoicesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The voice-settings dialog's draft.
 *
 * Lives in the screen's `activeDialog` field like every other dialog's draft (ADR-0036), which is
 * why this controller neither holds nor exposes it — a second copy here would be a second source of
 * truth, and would let the draft outlive the dialog that owns it.
 *
 * [seededVoiceLabel] is the saved voice's label the dialog opened with. While [availableVoices] is
 * still empty the voice can't have been changed (the dropdown has nothing to pick), so the label
 * names [draftVoiceId] until the list arrives and takes over.
 */
data class VoiceSettingsDraftState(
    val availableVoices: List<VoiceOption> = emptyList(),
    val draftVoiceId: String? = null,
    val draftSpeed: Float = 1f,
    val seededVoiceLabel: VoiceLabel? = null,
)

/**
 * The domain value a confirmed draft folds into — what [VoiceSettingsController.save] persists.
 * Carries the chosen voice's label data so a screen can name it later without the voice list — from
 * the list once it has arrived, from the seeded label before then. Left null when neither can name
 * the voice; the preferences repository resolves it at write time where it can.
 */
fun VoiceSettingsDraftState.toVoiceSettings(): VoiceSettings = VoiceSettings(
    speechRate = draftSpeed,
    voiceId = draftVoiceId,
    voiceLabel = availableVoices.firstOrNull { it.id == draftVoiceId }?.voiceLabel
        ?: seededVoiceLabel.takeIf { availableVoices.isEmpty() },
)

/**
 * Seeds, previews and saves the voice-settings dialog. Shared by feature:study and
 * feature:settings; each ViewModel injects its own instance (unscoped) and binds it to its own
 * viewModelScope. The voice list itself is cached process-wide behind [GetAvailableVoicesUseCase],
 * not here, so every screen shares one enumeration.
 *
 * Voice settings are session-scoped like every other study setting (`mode`, `ratedAttempts`): the
 * saved value lives on `StudySessionConfig`/`FastStudySessionRoute`/`RatedStudySessionRoute`, not
 * here. A screen hands the
 * current value to [seedDraft] itself rather than this controller tracking a subscription of its
 * own — one fewer place a value could disagree with the config the screen already has in state.
 */
class VoiceSettingsController @Inject constructor(
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val getAvailableVoices: GetAvailableVoicesUseCase,
    private val previewGateway: VoicePreviewGateway,
) {

    /**
     * The draft a newly opened dialog starts from — [current] with no voice list yet; the caller
     * follows up with [loadVoices], which answers without suspending once the list is cached.
     */
    fun seedDraft(current: VoiceSettings): VoiceSettingsDraftState = VoiceSettingsDraftState(
        draftVoiceId = current.voiceId,
        draftSpeed = current.speechRate,
        seededVoiceLabel = current.voiceLabel,
    )

    /**
     * Calls back with the voice list, enumerating it only on the first call of the app run. A
     * failed load answers with an empty list — the dialog shows no voices rather than an error —
     * and is retried by the next call.
     */
    @Suppress("TooGenericExceptionCaught")
    fun loadVoices(scope: CoroutineScope, onLoaded: (List<VoiceOption>) -> Unit) {
        scope.launch {
            val voices = try {
                getAvailableVoices()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load voices", exception)
                emptyList()
            }
            onLoaded(voices)
        }
    }

    fun preview(draft: VoiceSettingsDraftState) {
        previewGateway.preview(draft.draftVoiceId, draft.draftSpeed)
    }

    fun save(scope: CoroutineScope, draft: VoiceSettingsDraftState): VoiceSettings {
        val settings = draft.toVoiceSettings()
        scope.launch {
            saveStudySessionPreference(VoicePlayback(settings))
                .onFailure { Log.e(TAG, "Failed to save voice settings", it) }
        }
        previewGateway.stop()
        return settings
    }

    fun stopPreview() {
        previewGateway.stop()
    }

    private companion object {
        const val TAG = "VoiceSettingsController"
    }
}
