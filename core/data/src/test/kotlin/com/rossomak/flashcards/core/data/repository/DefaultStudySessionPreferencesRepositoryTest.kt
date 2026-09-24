package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.source.StudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SessionLength
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoicePlayback
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.VoiceOptionsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultStudySessionPreferencesRepositoryTest {

    private val localDataSource: StudySessionPreferencesLocalDataSource = mockk(relaxed = true)
    private val voiceOptionsRepository: VoiceOptionsRepository = mockk()

    private fun createRepository(): DefaultStudySessionPreferencesRepository =
        DefaultStudySessionPreferencesRepository(localDataSource, voiceOptionsRepository)

    @Test
    fun `saving a voice without a label resolves the label from the voice list`() = runTest {
        coEvery { voiceOptionsRepository.getAvailableVoices() } returns VOICES

        createRepository().save(VoicePlayback(UNLABELLED_SETTINGS))

        coVerify(exactly = 1) { localDataSource.save(VoicePlayback(UNLABELLED_SETTINGS.copy(voiceLabel = US_LABEL))) }
    }

    @Test
    fun `saving a voice that already has a label never reads the voice list`() = runTest {
        val labelledSettings = UNLABELLED_SETTINGS.copy(voiceLabel = US_LABEL)

        createRepository().save(VoicePlayback(labelledSettings))

        coVerify(exactly = 1) { localDataSource.save(VoicePlayback(labelledSettings)) }
        coVerify(exactly = 0) { voiceOptionsRepository.getAvailableVoices() }
    }

    @Test
    fun `saving settings without a voice never reads the voice list`() = runTest {
        val voicelessSettings = VoiceSettings(speechRate = SPEECH_RATE)

        createRepository().save(VoicePlayback(voicelessSettings))

        coVerify(exactly = 1) { localDataSource.save(VoicePlayback(voicelessSettings)) }
        coVerify(exactly = 0) { voiceOptionsRepository.getAvailableVoices() }
    }

    @Test
    fun `a failed voice list load still saves the voice without a label`() = runTest {
        coEvery { voiceOptionsRepository.getAvailableVoices() } throws IllegalStateException("engine down")

        createRepository().save(VoicePlayback(UNLABELLED_SETTINGS))

        coVerify(exactly = 1) { localDataSource.save(VoicePlayback(UNLABELLED_SETTINGS)) }
    }

    @Test
    fun `a voice missing from the voice list is saved without a label`() = runTest {
        coEvery { voiceOptionsRepository.getAvailableVoices() } returns emptyList()

        createRepository().save(VoicePlayback(UNLABELLED_SETTINGS))

        coVerify(exactly = 1) { localDataSource.save(VoicePlayback(UNLABELLED_SETTINGS)) }
    }

    @Test
    fun `non-voice preferences pass through untouched`() = runTest {
        createRepository().save(SessionLength(SESSION_LENGTH))

        coVerify(exactly = 1) { localDataSource.save(SessionLength(SESSION_LENGTH)) }
        coVerify(exactly = 0) { voiceOptionsRepository.getAvailableVoices() }
    }

    private companion object {
        const val SPEECH_RATE = 1.5f
        const val SESSION_LENGTH = 20
        const val US_VOICE_ID = "en-us-x-1"
        val US_LABEL = VoiceLabel(countryCode = "US", variantIndex = 1)
        val UNLABELLED_SETTINGS = VoiceSettings(speechRate = SPEECH_RATE, voiceId = US_VOICE_ID)
        val VOICES = listOf(
            VoiceOption(id = US_VOICE_ID, countryCode = "US", variantIndex = 1),
            VoiceOption(id = "en-gb-x-2", countryCode = "GB", variantIndex = 1),
        )
    }
}
