package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.rossomak.flashcards.core.data.di.UserPreferencesDataStore
import com.rossomak.flashcards.core.domain.model.XpConfig
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Keeps the last fetched XP configuration under its own `xp_config_` keys in the app's one
 * preferences file. All twelve values are written in one edit, so a read sees either a whole
 * configuration or none.
 */
class DataStoreXpConfigLocalDataSource @Inject constructor(
    @param:UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
) : XpConfigLocalDataSource {

    override suspend fun getXpConfig(): XpConfig? {
        val prefs = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        if (ALL_KEYS.any { key -> key !in prefs }) return null
        return XpConfig(
            newCardStudied = prefs.requireValue(NEW_CARD_STUDIED_KEY),
            cardMastered = prefs.requireValue(CARD_MASTERED_KEY),
            cardPartial = prefs.requireValue(CARD_PARTIAL_KEY),
            masteryDefended = prefs.requireValue(MASTERY_DEFENDED_KEY),
            cardDemastered = prefs.requireValue(CARD_DEMASTERED_KEY),
            sessionCompleted = prefs.requireValue(SESSION_COMPLETED_KEY),
            dailyGoalMet = prefs.requireValue(DAILY_GOAL_MET_KEY),
            streakPerDay = prefs.requireValue(STREAK_PER_DAY_KEY),
            streakMaxPerDay = prefs.requireValue(STREAK_MAX_PER_DAY_KEY),
            minuteStudied = prefs.requireValue(MINUTE_STUDIED_KEY),
            levelCurveBase = prefs.requireValue(LEVEL_CURVE_BASE_KEY),
            levelCurveExponent = prefs.requireValue(LEVEL_CURVE_EXPONENT_KEY),
        )
    }

    override suspend fun save(config: XpConfig) {
        dataStore.edit { prefs ->
            prefs[NEW_CARD_STUDIED_KEY] = config.newCardStudied
            prefs[CARD_MASTERED_KEY] = config.cardMastered
            prefs[CARD_PARTIAL_KEY] = config.cardPartial
            prefs[MASTERY_DEFENDED_KEY] = config.masteryDefended
            prefs[CARD_DEMASTERED_KEY] = config.cardDemastered
            prefs[SESSION_COMPLETED_KEY] = config.sessionCompleted
            prefs[DAILY_GOAL_MET_KEY] = config.dailyGoalMet
            prefs[STREAK_PER_DAY_KEY] = config.streakPerDay
            prefs[STREAK_MAX_PER_DAY_KEY] = config.streakMaxPerDay
            prefs[MINUTE_STUDIED_KEY] = config.minuteStudied
            prefs[LEVEL_CURVE_BASE_KEY] = config.levelCurveBase
            prefs[LEVEL_CURVE_EXPONENT_KEY] = config.levelCurveExponent
        }
    }

    private fun <T> Preferences.requireValue(key: Preferences.Key<T>): T = checkNotNull(this[key]) { "missing ${key.name}" }

    private companion object {
        val NEW_CARD_STUDIED_KEY = intPreferencesKey("xp_config_new_card_studied")
        val CARD_MASTERED_KEY = intPreferencesKey("xp_config_card_mastered")
        val CARD_PARTIAL_KEY = intPreferencesKey("xp_config_card_partial")
        val MASTERY_DEFENDED_KEY = intPreferencesKey("xp_config_mastery_defended")
        val CARD_DEMASTERED_KEY = intPreferencesKey("xp_config_card_demastered")
        val SESSION_COMPLETED_KEY = intPreferencesKey("xp_config_session_completed")
        val DAILY_GOAL_MET_KEY = intPreferencesKey("xp_config_daily_goal_met")
        val STREAK_PER_DAY_KEY = intPreferencesKey("xp_config_streak_per_day")
        val STREAK_MAX_PER_DAY_KEY = intPreferencesKey("xp_config_streak_max_per_day")
        val MINUTE_STUDIED_KEY = intPreferencesKey("xp_config_minute_studied")
        val LEVEL_CURVE_BASE_KEY = doublePreferencesKey("xp_config_level_curve_base")
        val LEVEL_CURVE_EXPONENT_KEY = doublePreferencesKey("xp_config_level_curve_exponent")
        val ALL_KEYS = listOf(
            NEW_CARD_STUDIED_KEY,
            CARD_MASTERED_KEY,
            CARD_PARTIAL_KEY,
            MASTERY_DEFENDED_KEY,
            CARD_DEMASTERED_KEY,
            SESSION_COMPLETED_KEY,
            DAILY_GOAL_MET_KEY,
            STREAK_PER_DAY_KEY,
            STREAK_MAX_PER_DAY_KEY,
            MINUTE_STUDIED_KEY,
            LEVEL_CURVE_BASE_KEY,
            LEVEL_CURVE_EXPONENT_KEY,
        )
    }
}
