package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenVoiceAnsweringInfo
import com.rossomak.flashcards.core.domain.model.UserPreferences
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
class DataStoreUserPreferencesLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.newFile("user_preferences.preferences_pb")
        }
    }

    private fun createLocalDataSource(): DataStoreUserPreferencesLocalDataSource =
        DataStoreUserPreferencesLocalDataSource(dataStore)

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `userPreferences emits defaults when nothing is persisted`() = runTest {
        val preferences = createLocalDataSource().userPreferences().first()

        preferences shouldBe UserPreferences()
    }

    @Test
    fun `save then read round-trips the daily goal`() = runTest {
        val localDataSource = createLocalDataSource()

        localDataSource.save(DailyGoalMinutes(45))
        val preferences = localDataSource.userPreferences().first()

        preferences.dailyGoalMinutes shouldBe 45
    }

    @Test
    fun `saving a daily goal above the maximum clamps it`() = runTest {
        val localDataSource = createLocalDataSource()

        localDataSource.save(DailyGoalMinutes(DailyGoal.MAX_MINUTES + 100))
        val preferences = localDataSource.userPreferences().first()

        preferences.dailyGoalMinutes shouldBe DailyGoal.MAX_MINUTES
    }

    @Test
    fun `saving has seen onboarding persists independently of the daily goal`() = runTest {
        val localDataSource = createLocalDataSource()
        localDataSource.save(DailyGoalMinutes(30))

        localDataSource.save(HasSeenOnboarding(true))
        val preferences = localDataSource.userPreferences().first()

        preferences shouldBe UserPreferences(hasSeenOnboarding = true, dailyGoalMinutes = 30)
    }

    @Test
    fun `save then read round-trips the voice answering info seen flag`() = runTest {
        val localDataSource = createLocalDataSource()

        localDataSource.save(HasSeenVoiceAnsweringInfo(true))
        val preferences = localDataSource.userPreferences().first()

        preferences.hasSeenVoiceAnsweringInfo shouldBe true
    }
}
