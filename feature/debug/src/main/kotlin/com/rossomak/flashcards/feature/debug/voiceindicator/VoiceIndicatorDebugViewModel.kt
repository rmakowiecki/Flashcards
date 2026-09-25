package com.rossomak.flashcards.feature.debug.voiceindicator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Feeds the voice capture indicator a synthetic level stream: two sines plus noise with random
 * silent gaps, newest level innermost.
 */
@HiltViewModel
class VoiceIndicatorDebugViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(VoiceIndicatorDebugScreenState())
    val state: StateFlow<VoiceIndicatorDebugScreenState> = _state.asStateFlow()

    /** Ticks only while collected; restarts at rest on resubscription. */
    val levels: StateFlow<ImmutableList<Float>> = flow {
        var history = RestLevels
        var elapsedMillis = 0L
        var silentGapEndMillis = 0L
        while (true) {
            val intervalMillis = _state.value.levelIntervalMillis
            delay(intervalMillis.milliseconds)
            elapsedMillis += intervalMillis
            if (elapsedMillis >= silentGapEndMillis && Random.nextFloat() < SILENT_GAP_CHANCE_PER_TICK) {
                silentGapEndMillis = elapsedMillis + Random.nextLong(SILENT_GAP_MIN_MILLIS, SILENT_GAP_MAX_MILLIS)
            }
            val isSilent = !_state.value.isSpeechSimulated || elapsedMillis < silentGapEndMillis
            val level = if (isSilent) 0f else syntheticSpeechLevel(elapsedMillis)
            history = (listOf(level) + history).take(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT)
                .toImmutableList()
            emit(history)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT.inWholeMilliseconds),
        initialValue = RestLevels,
    )

    fun onSpeechSimulatedChange(isSpeechSimulated: Boolean) {
        _state.update { it.copy(isSpeechSimulated = isSpeechSimulated) }
    }

    fun onLevelIntervalChange(levelIntervalMillis: Int) {
        _state.update { it.copy(levelIntervalMillis = levelIntervalMillis) }
    }

    private fun syntheticSpeechLevel(elapsedMillis: Long): Float {
        val seconds = elapsedMillis / MILLIS_PER_SECOND
        val syllables = sin(2 * PI * SYLLABLE_RATE_HZ * seconds)
        val phrasing = sin(2 * PI * PHRASE_RATE_HZ * seconds)
        val noise = Random.nextFloat() * 2 - 1
        val level = BASE_LEVEL + SYLLABLE_WEIGHT * syllables + PHRASE_WEIGHT * phrasing + NOISE_WEIGHT * noise
        return level.toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        val STOP_TIMEOUT = 5.seconds
        val RestLevels: ImmutableList<Float> =
            List(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT) { 0f }.toImmutableList()

        const val MILLIS_PER_SECOND = 1000.0
        const val SYLLABLE_RATE_HZ = 3.7
        const val PHRASE_RATE_HZ = 0.6
        const val BASE_LEVEL = 0.5
        const val SYLLABLE_WEIGHT = 0.3
        const val PHRASE_WEIGHT = 0.15
        const val NOISE_WEIGHT = 0.12

        const val SILENT_GAP_CHANCE_PER_TICK = 0.02f
        const val SILENT_GAP_MIN_MILLIS = 300L
        const val SILENT_GAP_MAX_MILLIS = 900L
    }
}
