package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.source.StudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.domain.model.StudySessionPreference
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoicePlayback
import com.rossomak.flashcards.core.domain.model.StudySessionPreferences
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.voiceLabel
import com.rossomak.flashcards.core.domain.repository.StudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.VoiceOptionsRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Guarantees a saved voice carries its label whenever the voice list can name it: a [VoicePlayback]
 * arriving without one (its dialog was confirmed before the list loaded) is labelled here from the
 * process-wide voice cache, so no save path has to remember to. When the list can't name the voice —
 * the load failed, or the voice is no longer installed — the voice is saved unlabelled rather than
 * dropped, and a reader resolves the label later.
 */
class DefaultStudySessionPreferencesRepository @Inject constructor(
    private val localDataSource: StudySessionPreferencesLocalDataSource,
    private val voiceOptionsRepository: VoiceOptionsRepository,
) : StudySessionPreferencesRepository {

    override fun studySessionPreferences(): Flow<StudySessionPreferences> = localDataSource.studySessionPreferences()

    override suspend fun save(preference: StudySessionPreference) {
        val labelledPreference = when (preference) {
            is VoicePlayback -> VoicePlayback(preference.value.withResolvedLabel())
            else -> preference
        }
        localDataSource.save(labelledPreference)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun VoiceSettings.withResolvedLabel(): VoiceSettings {
        if (voiceId == null || voiceLabel != null) return this
        val voices = try {
            voiceOptionsRepository.getAvailableVoices()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logw(exception) { "Voice list unavailable; saving voice $voiceId without a label" }
            return this
        }
        val voice = voices.firstOrNull { it.id == voiceId }
        if (voice == null) logw { "Voice $voiceId not installed; saving it without a label" }
        return copy(voiceLabel = voice?.voiceLabel)
    }
}
