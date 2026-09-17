package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.rossomak.flashcards.core.data.di.UserPreferencesDataStore
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.UserPreference
import com.rossomak.flashcards.core.domain.model.UserPreference.CacheSeed
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.model.UserPreference.VoiceAnswerConsent
import com.rossomak.flashcards.core.domain.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreUserPreferencesLocalDataSource @Inject constructor(
    @param:UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
) : UserPreferencesLocalDataSource {

    // Any read failure — not just IOException — falls back to defaults rather than propagating: a
    // corrupted or unreadable preferences file must never crash a caller that only wants "give me
    // *some* value" (e.g. StudySessionSummaryViewModel reading dailyGoalMinutes before XP submission).
    // CancellationException is rethrown, never swallowed, so structured concurrency still works.
    override fun userPreferences(): Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyPreferences())
        }
        .map { prefs ->
            UserPreferences(
                hasSeenOnboarding = prefs[HAS_SEEN_ONBOARDING_KEY] ?: DEFAULT_HAS_SEEN_ONBOARDING,
                dailyGoalMinutes = prefs[DAILY_GOAL_MINUTES_KEY] ?: DailyGoal.DEFAULT_MINUTES,
                voiceAnswerConsentGranted = prefs[VOICE_ANSWER_CONSENT_KEY] ?: DEFAULT_VOICE_ANSWER_CONSENT_GRANTED,
                localCacheSeed = prefs[CACHE_SEED_KEY],
            )
        }

    override suspend fun save(preference: UserPreference) {
        dataStore.edit { prefs ->
            when (preference) {
                is DailyGoalMinutes -> prefs[DAILY_GOAL_MINUTES_KEY] = DailyGoal.coerce(preference.value)
                is HasSeenOnboarding -> prefs[HAS_SEEN_ONBOARDING_KEY] = preference.value
                is VoiceAnswerConsent -> prefs[VOICE_ANSWER_CONSENT_KEY] = preference.value
                is CacheSeed -> prefs[CACHE_SEED_KEY] = preference.value
            }
        }
    }

    private companion object {
        val DEFAULT_HAS_SEEN_ONBOARDING = UserPreferences().hasSeenOnboarding
        val DEFAULT_VOICE_ANSWER_CONSENT_GRANTED = UserPreferences().voiceAnswerConsentGranted
        val HAS_SEEN_ONBOARDING_KEY = booleanPreferencesKey("has_seen_onboarding")
        val DAILY_GOAL_MINUTES_KEY = intPreferencesKey("daily_goal_minutes")
        val VOICE_ANSWER_CONSENT_KEY = booleanPreferencesKey("voice_answer_consent")
        val CACHE_SEED_KEY = intPreferencesKey("cache_seed")
    }
}
