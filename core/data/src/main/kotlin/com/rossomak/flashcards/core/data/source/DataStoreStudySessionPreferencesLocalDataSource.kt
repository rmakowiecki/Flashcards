package com.rossomak.flashcards.core.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rossomak.flashcards.core.data.di.UserPreferencesDataStore
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.StudySessionPreference
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
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreStudySessionPreferencesLocalDataSource @Inject constructor(
    @param:UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
) : StudySessionPreferencesLocalDataSource {

    override fun studySessionPreferences(): Flow<StudySessionPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            StudySessionPreferences(
                defaultStudyMode = prefs[DEFAULT_STUDY_MODE_KEY].toStudyMode(),
                voiceAnsweringEnabled = prefs[VOICE_ANSWERING_ENABLED_KEY] ?: DEFAULT_VOICE_ANSWERING_ENABLED,
                ratedAttempts = prefs[RATED_ATTEMPTS_KEY] ?: StudySessionConfig.DEFAULT_RATED_ATTEMPTS,
                readAloudEnabled = prefs[READ_ALOUD_ENABLED_KEY] ?: DEFAULT_READ_ALOUD_ENABLED,
                partialRatingCardRequeueingEnabled = prefs[PARTIAL_RATING_CARD_REQUEUEING_ENABLED_KEY]
                    ?: DEFAULT_PARTIAL_RATING_CARD_REQUEUEING_ENABLED,
                sessionLength = prefs[SESSION_LENGTH_KEY] ?: StudySessionConfig.DEFAULT_LENGTH,
                sortOrder = prefs[SORT_ORDER_KEY].toSortOrder(),
                voiceSettings = VoiceSettings(
                    speechRate = prefs[VOICE_SPEECH_RATE_KEY] ?: DEFAULT_VOICE_SPEECH_RATE,
                    voiceId = prefs[VOICE_ID_KEY],
                    voiceLabel = prefs.toVoiceLabel(),
                ),
                subcategoryCountRange = prefs.toSubcategoryCountRange(),
            )
        }

    override suspend fun save(preference: StudySessionPreference) {
        dataStore.edit { prefs ->
            when (preference) {
                is DefaultStudyMode -> prefs[DEFAULT_STUDY_MODE_KEY] = preference.value.name
                is VoiceAnsweringEnabled -> prefs[VOICE_ANSWERING_ENABLED_KEY] = preference.value
                is RatedAttempts -> prefs[RATED_ATTEMPTS_KEY] = preference.value
                is ReadAloudEnabled -> prefs[READ_ALOUD_ENABLED_KEY] = preference.value
                is PartialRatingCardRequeueingEnabled ->
                    prefs[PARTIAL_RATING_CARD_REQUEUEING_ENABLED_KEY] = preference.value
                is SessionLength -> prefs[SESSION_LENGTH_KEY] = preference.value
                is SortOrder -> prefs[SORT_ORDER_KEY] = preference.value.name
                is SubcategoryCountRange -> {
                    prefs[SUBCATEGORY_COUNT_MIN_KEY] = preference.value.first
                    prefs[SUBCATEGORY_COUNT_MAX_KEY] = preference.value.last
                }
                is VoicePlayback -> {
                    prefs[VOICE_SPEECH_RATE_KEY] = preference.value.speechRate
                    val voiceId = preference.value.voiceId
                    if (voiceId != null) {
                        prefs[VOICE_ID_KEY] = voiceId
                    } else {
                        prefs.remove(VOICE_ID_KEY)
                    }
                    val voiceLabel = preference.value.voiceLabel
                    if (voiceLabel != null) {
                        prefs[VOICE_COUNTRY_CODE_KEY] = voiceLabel.countryCode
                        prefs[VOICE_VARIANT_INDEX_KEY] = voiceLabel.variantIndex
                    } else {
                        prefs.remove(VOICE_COUNTRY_CODE_KEY)
                        prefs.remove(VOICE_VARIANT_INDEX_KEY)
                    }
                }
            }
        }
    }

    /**
     * Falls back to the default rather than throwing: a stored name can go stale if [StudyMode]
     * ever drops a constant, and a preference that can't be read is not worth crashing a launch
     * over.
     */
    private fun String?.toStudyMode(): StudyMode =
        StudyMode.entries.firstOrNull { it.name == this } ?: DEFAULT_STUDY_MODE

    private fun String?.toSortOrder(): FlashcardSortOrder =
        FlashcardSortOrder.entries.firstOrNull { it.name == this } ?: DEFAULT_SORT_ORDER

    /** Null unless both halves are stored. */
    private fun Preferences.toVoiceLabel(): VoiceLabel? {
        val countryCode = this[VOICE_COUNTRY_CODE_KEY] ?: return null
        val variantIndex = this[VOICE_VARIANT_INDEX_KEY] ?: return null
        return VoiceLabel(countryCode = countryCode, variantIndex = variantIndex)
    }

    private fun Preferences.toSubcategoryCountRange(): IntRange {
        val min = this[SUBCATEGORY_COUNT_MIN_KEY] ?: DEFAULT_SUBCATEGORY_COUNT_RANGE.first
        val max = this[SUBCATEGORY_COUNT_MAX_KEY] ?: DEFAULT_SUBCATEGORY_COUNT_RANGE.last
        return min..max
    }

    private companion object {
        val DEFAULT_STUDY_MODE = StudySessionPreferences().defaultStudyMode
        val DEFAULT_VOICE_ANSWERING_ENABLED = StudySessionPreferences().voiceAnsweringEnabled
        val DEFAULT_READ_ALOUD_ENABLED = StudySessionPreferences().readAloudEnabled
        val DEFAULT_PARTIAL_RATING_CARD_REQUEUEING_ENABLED =
            StudySessionPreferences().partialRatingCardRequeueingEnabled
        val DEFAULT_SORT_ORDER = StudySessionPreferences().sortOrder
        val DEFAULT_VOICE_SPEECH_RATE = VoiceSettings().speechRate
        val DEFAULT_SUBCATEGORY_COUNT_RANGE = StudySessionPreferences().subcategoryCountRange
        val DEFAULT_STUDY_MODE_KEY = stringPreferencesKey("default_study_mode")
        val VOICE_ANSWERING_ENABLED_KEY = booleanPreferencesKey("voice_answering_enabled")
        val RATED_ATTEMPTS_KEY = intPreferencesKey("rated_attempts")
        val READ_ALOUD_ENABLED_KEY = booleanPreferencesKey("read_aloud_enabled")
        val PARTIAL_RATING_CARD_REQUEUEING_ENABLED_KEY =
            booleanPreferencesKey("partial_rating_card_requeueing_enabled")
        val SESSION_LENGTH_KEY = intPreferencesKey("session_length")
        val SORT_ORDER_KEY = stringPreferencesKey("sort_order")
        val VOICE_SPEECH_RATE_KEY = floatPreferencesKey("voice_speech_rate")
        val VOICE_ID_KEY = stringPreferencesKey("voice_id")
        val VOICE_COUNTRY_CODE_KEY = stringPreferencesKey("voice_country_code")
        val VOICE_VARIANT_INDEX_KEY = intPreferencesKey("voice_variant_index")
        val SUBCATEGORY_COUNT_MIN_KEY = intPreferencesKey("subcategory_count_min")
        val SUBCATEGORY_COUNT_MAX_KEY = intPreferencesKey("subcategory_count_max")
    }
}
