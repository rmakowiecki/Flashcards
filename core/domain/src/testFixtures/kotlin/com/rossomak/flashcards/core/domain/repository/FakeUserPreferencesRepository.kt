package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserPreference
import com.rossomak.flashcards.core.domain.model.UserPreference.CacheSeed
import com.rossomak.flashcards.core.domain.model.UserPreference.DailyGoalMinutes
import com.rossomak.flashcards.core.domain.model.UserPreference.HasHiddenFavoritesHint
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenOnboarding
import com.rossomak.flashcards.core.domain.model.UserPreference.HasSeenVoiceAnsweringInfo
import com.rossomak.flashcards.core.domain.model.UserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take

class FakeUserPreferencesRepository : UserPreferencesRepository {

    val preferences = MutableStateFlow(UserPreferences())

    /** Set to make [save] throw, so callers can exercise the failure path. */
    var saveError: Throwable? = null

    /** When set, [userPreferences] suspends on this before its first emission, so a test can hold the read unresolved. */
    var userPreferencesReadGate: CompletableDeferred<Unit>? = null

    /** When set, [userPreferences] throws this before its first emission. */
    var userPreferencesReadFailure: Throwable? = null

    /** When set, [userPreferences] completes after this many emissions instead of staying open. */
    var userPreferencesEmissionLimit: Int? = null

    override fun userPreferences(): Flow<UserPreferences> = flow {
        userPreferencesReadGate?.await()
        userPreferencesReadFailure?.let { throw it }
        emitAll(userPreferencesEmissionLimit?.let(preferences::take) ?: preferences)
    }

    override suspend fun save(preference: UserPreference) {
        saveError?.let { throw it }
        preferences.value = when (preference) {
            is DailyGoalMinutes -> preferences.value.copy(dailyGoalMinutes = preference.value)
            is HasSeenOnboarding -> preferences.value.copy(hasSeenOnboarding = preference.value)
            is HasSeenVoiceAnsweringInfo -> preferences.value.copy(hasSeenVoiceAnsweringInfo = preference.value)
            is HasHiddenFavoritesHint -> preferences.value.copy(hasHiddenFavoritesHint = preference.value)
            is CacheSeed -> preferences.value.copy(localCacheSeed = preference.value)
        }
    }
}
