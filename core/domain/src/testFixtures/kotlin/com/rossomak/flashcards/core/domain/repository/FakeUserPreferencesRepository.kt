package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserPreference
import com.rossomak.flashcards.core.domain.model.UserPreference.CacheSeed
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenVoiceAnsweringInfo
import com.rossomak.flashcards.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {

    val preferences = MutableStateFlow(UserPreferences())

    /** Set to make [save] throw, so callers can exercise the failure path. */
    var saveError: Throwable? = null

    override fun userPreferences(): Flow<UserPreferences> = preferences

    override suspend fun save(preference: UserPreference) {
        saveError?.let { throw it }
        preferences.value = when (preference) {
            is DailyGoalMinutes -> preferences.value.copy(dailyGoalMinutes = preference.value)
            is HasSeenOnboarding -> preferences.value.copy(hasSeenOnboarding = preference.value)
            is HasSeenVoiceAnsweringInfo -> preferences.value.copy(hasSeenVoiceAnsweringInfo = preference.value)
            is CacheSeed -> preferences.value.copy(localCacheSeed = preference.value)
        }
    }
}
