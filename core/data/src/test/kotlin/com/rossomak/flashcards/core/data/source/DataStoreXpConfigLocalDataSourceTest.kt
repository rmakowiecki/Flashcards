package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rossomak.flashcards.core.domain.model.XpConfig
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreXpConfigLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            temporaryFolder.newFile("user_preferences.preferences_pb")
        }
    }

    private val localDataSource by lazy { DataStoreXpConfigLocalDataSource(dataStore) }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `getXpConfig is null when nothing has been saved`() = runTest {
        localDataSource.getXpConfig() shouldBe null
    }

    @Test
    fun `save then read round-trips every field`() = runTest {
        val config = XpConfig(
            newCardStudied = 3,
            cardMastered = 7,
            cardPartial = 11,
            masteryDefended = 13,
            cardDemastered = -17,
            sessionCompleted = 19,
            dailyGoalMet = 29,
            streakPerDay = 31,
            streakMaxPerDay = 37,
            levelBaseCost = 1234,
            levelCostSteps = listOf(41, 43, 47),
            levelsPerCostStep = 3,
        )

        localDataSource.save(config)

        localDataSource.getXpConfig() shouldBe config
    }

    @Test
    fun `a later save replaces the earlier one`() = runTest {
        localDataSource.save(XpConfig(cardMastered = 200))
        localDataSource.save(XpConfig(cardMastered = 300))

        localDataSource.getXpConfig() shouldBe XpConfig(cardMastered = 300)
    }

    @Test
    fun `a stored step list with a blank or malformed entry reads as no configuration`() = runTest {
        BAD_STEP_LISTS.forEach { badStepList ->
            localDataSource.save(XpConfig())
            overwriteSteps(badStepList)

            localDataSource.getXpConfig() shouldBe null
        }
    }

    @Test
    fun `a stored step list with whitespace around its entries still reads`() = runTest {
        localDataSource.save(XpConfig())
        overwriteSteps(" 250 , 500 ")

        localDataSource.getXpConfig() shouldBe XpConfig(levelCostSteps = listOf(250, 500))
    }

    @Test
    fun `a stored configuration that fails validation reads as no configuration`() = runTest {
        localDataSource.save(XpConfig())
        dataStore.edit { prefs -> prefs[intPreferencesKey("xp_config_levels_per_cost_step")] = 0 }

        localDataSource.getXpConfig() shouldBe null
    }

    @Test
    fun `a configuration stored under the old level curve keys reads as no configuration`() = runTest {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("xp_config_new_card_studied")] = 10
            prefs[intPreferencesKey("xp_config_level_curve_base")] = 1000
            prefs[intPreferencesKey("xp_config_level_curve_exponent")] = 2
        }

        localDataSource.getXpConfig() shouldBe null
    }

    private suspend fun overwriteSteps(storedSteps: String) {
        dataStore.edit { prefs -> prefs[stringPreferencesKey("xp_config_level_cost_steps")] = storedSteps }
    }

    private companion object {
        val BAD_STEP_LISTS = listOf("", " ", "250,,500", "250,abc", "250,0", "250,-500")
    }
}
