package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.DefaultStudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.PartialRatingCardRequeueingEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.RatedAttempts
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.ReadAloudEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SessionLength
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SubcategoryCountRange
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoiceAnsweringEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoicePlayback
import com.rossomak.flashcards.core.domain.model.StudySessionPreferences
import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreStudySessionPreferencesLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.newFile("user_preferences.preferences_pb")
        }
    }

    private fun createLocalDataSource(): DataStoreStudySessionPreferencesLocalDataSource =
        DataStoreStudySessionPreferencesLocalDataSource(dataStore)

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `studySessionPreferences emits defaults when nothing is persisted`() = runTest {
        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences shouldBe StudySessionPreferences()
    }

    @Test
    fun `save then read round-trips every field`() = runTest {
        val localDataSource = createLocalDataSource()

        localDataSource.save(DefaultStudyMode(StudyMode.Fast))
        localDataSource.save(VoiceAnsweringEnabled(true))
        localDataSource.save(RatedAttempts(2))
        localDataSource.save(ReadAloudEnabled(true))
        localDataSource.save(PartialRatingCardRequeueingEnabled(false))
        localDataSource.save(SessionLength(35))
        localDataSource.save(SortOrder(FlashcardSortOrder.HardestFirst))
        localDataSource.save(VoicePlayback(VoiceSettings(speechRate = 1.5f, voiceId = "en-us-x-1")))
        localDataSource.save(SubcategoryCountRange(2..4))
        val preferences = localDataSource.studySessionPreferences().first()

        preferences shouldBe StudySessionPreferences(
            defaultStudyMode = StudyMode.Fast,
            voiceAnsweringEnabled = true,
            ratedAttempts = 2,
            readAloudEnabled = true,
            partialRatingCardRequeueingEnabled = false,
            sessionLength = 35,
            sortOrder = FlashcardSortOrder.HardestFirst,
            voiceSettings = VoiceSettings(speechRate = 1.5f, voiceId = "en-us-x-1"),
            subcategoryCountRange = 2..4,
        )
    }

    @Test
    fun `subcategoryCountRange defaults to StudySessionConfig's default`() = runTest {
        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences.subcategoryCountRange shouldBe StudySessionConfig.DEFAULT_SUBCATEGORY_COUNT_RANGE
    }

    @Test
    fun `saving a null voice id clears a previously stored voice id`() = runTest {
        val localDataSource = createLocalDataSource()
        val speechRate = 1.25f
        localDataSource.save(VoicePlayback(VoiceSettings(speechRate = speechRate, voiceId = "en-us-x-1")))

        localDataSource.save(VoicePlayback(VoiceSettings(speechRate = speechRate, voiceId = null)))
        val preferences = localDataSource.studySessionPreferences().first()

        preferences.voiceSettings.voiceId shouldBe null
        preferences.voiceSettings.speechRate shouldBe speechRate
    }

    @Test
    fun `voice label round-trips next to the voice id`() = runTest {
        val localDataSource = createLocalDataSource()
        val voiceSettings = VoiceSettings(speechRate = 1.25f, voiceId = "en-gb-x-2", voiceLabel = VoiceLabel(countryCode = "GB", variantIndex = 2))

        localDataSource.save(VoicePlayback(voiceSettings))
        val preferences = localDataSource.studySessionPreferences().first()

        preferences.voiceSettings shouldBe voiceSettings
    }

    @Test
    fun `a voice saved without label data reads back as label-missing`() = runTest {
        dataStore.edit { it[stringPreferencesKey("voice_id")] = "en-us-x-1" }

        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences.voiceSettings.voiceId shouldBe "en-us-x-1"
        preferences.voiceSettings.voiceLabel shouldBe null
    }

    @Test
    fun `saving settings without a label clears a previously stored label`() = runTest {
        val localDataSource = createLocalDataSource()
        localDataSource.save(
            VoicePlayback(VoiceSettings(voiceId = "en-us-x-1", voiceLabel = VoiceLabel(countryCode = "US", variantIndex = 1))),
        )

        localDataSource.save(VoicePlayback(VoiceSettings(voiceId = "en-us-x-3")))
        val preferences = localDataSource.studySessionPreferences().first()

        preferences.voiceSettings.voiceLabel shouldBe null
    }

    @Test
    fun `an unknown persisted study mode falls back to the default`() = runTest {
        dataStore.edit { it[stringPreferencesKey("default_study_mode")] = "NoLongerExists" }

        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences.defaultStudyMode shouldBe StudySessionPreferences().defaultStudyMode
    }

    @Test
    fun `an unknown persisted sort order falls back to the default`() = runTest {
        dataStore.edit { it[stringPreferencesKey("sort_order")] = "NoLongerExists" }

        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences.sortOrder shouldBe FlashcardSortOrder.Default
    }

    @Test
    fun `sessionLength defaults to StudySessionConfig's default`() = runTest {
        val preferences = createLocalDataSource().studySessionPreferences().first()

        preferences.sessionLength shouldBe StudySessionConfig.DEFAULT_LENGTH
    }
}
