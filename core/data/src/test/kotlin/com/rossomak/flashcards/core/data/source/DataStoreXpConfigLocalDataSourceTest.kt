package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
            minuteStudied = 23,
            levelCurveBase = 1234.5,
            levelCurveExponent = 1.75,
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
}
