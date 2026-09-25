package com.rossomak.flashcards.core.ui.voice

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logw
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
 * The voice-settings dialog's draft, held in the screen's `activeDialog` (ADR-0036).
 *
 * [seededVoiceLabel] names [draftVoiceId] only while [availableVoices] is empty: the dropdown has
 * nothing to pick until then, so the voice can't have changed.
 */
data class VoiceSettingsDraftState(
    val availableVoices: List<VoiceOption> = emptyList(),
    val draftVoiceId: String? = null,
    val draftSpeed: Float = 1f,
    val seededVoiceLabel: VoiceLabel? = null,
)

/** Label from the list, else the seeded label; null when neither names the voice. */
fun VoiceSettingsDraftState.toVoiceSettings(): VoiceSettings = VoiceSettings(
    speechRate = draftSpeed,
    voiceId = draftVoiceId,
    voiceLabel = availableVoices.firstOrNull { it.id == draftVoiceId }?.voiceLabel
        ?: seededVoiceLabel.takeIf { availableVoices.isEmpty() },
)

/**
 * Seeds, previews and saves the voice-settings dialog; one unscoped instance per ViewModel. The
 * screen passes the current value to [seedDraft]; the voice list is cached behind
 * [GetAvailableVoicesUseCase].
 */
class VoiceSettingsController @Inject constructor(
    private val saveStudySessionPreference: SaveStudySessionPreferenceUseCase,
    private val getAvailableVoices: GetAvailableVoicesUseCase,
    private val previewGateway: VoicePreviewGateway,
) {

    /** The draft a newly opened dialog starts from; follow up with [loadVoices]. */
    fun seedDraft(current: VoiceSettings): VoiceSettingsDraftState = VoiceSettingsDraftState(
        draftVoiceId = current.voiceId,
        draftSpeed = current.speechRate,
        seededVoiceLabel = current.voiceLabel,
    )

    /** Calls back with the voice list, or an empty one if loading failed; the next call retries. */
    @Suppress("TooGenericExceptionCaught")
    fun loadVoices(scope: CoroutineScope, onLoaded: (List<VoiceOption>) -> Unit) {
        scope.launch {
            val voices = try {
                getAvailableVoices()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logw(exception) { "Voice list unavailable" }
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
                .onFailure { loge(it) { "Failed to save voice settings" } }
        }
        previewGateway.stop()
        return settings
    }

    fun stopPreview() {
        previewGateway.stop()
    }
}
