package com.rossomak.flashcards.core.ui.composables.voice

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

private val VOICE_BARS_LEVELS_STOP_TIMEOUT = 5.seconds

/**
 * Shapes a raw voice level (`0..1`, microphone input or played-back audio) into the bar levels
 * [FlashcardsVoiceCaptureIndicator] draws, starting at
 * [FlashcardsVoiceCaptureIndicatorDefaults.restLevels].
 *
 * The levels never decide whether the bars show: a raw level of 0 only lets them sink to rest
 * height. Folding the bars away stays the caller's choice through the indicator's own `isActive`.
 *
 * Shaped only while the returned flow has subscribers, and kept for a few seconds after the last
 * one leaves so a configuration change does not restart the wave.
 */
fun Flow<Float>.stateInVoiceBarsLevels(scope: CoroutineScope): StateFlow<ImmutableList<Float>> = VoiceLevelWaveShaper()
    .shape(this)
    .map { levels -> levels.toImmutableList() }
    .stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = VOICE_BARS_LEVELS_STOP_TIMEOUT.inWholeMilliseconds),
        initialValue = FlashcardsVoiceCaptureIndicatorDefaults.restLevels,
    )

/**
 * Shapes a scalar level into an outward-traveling wave of [barCount] bar levels, index 0 newest.
 * Every [interval] it takes the maximum level seen in that window, so short plosives between
 * samples are not lost, and shifts it into index 0.
 *
 * The interval is the wave's propagation speed; the indicator animates between snapshots over the
 * same interval. When the level drops to 0 the wave decays to all zeros over [barCount] intervals
 * and then emits nothing more until the level rises again. While it rests there, the interval timer
 * is suspended too, so a silent input costs no periodic wakeups; the first tick after the level
 * rises comes one [interval] later.
 */
internal class VoiceLevelWaveShaper(
    private val barCount: Int = FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT,
    private val interval: Duration = FlashcardsVoiceCaptureIndicatorDefaults.LEVEL_INTERVAL_MILLIS.milliseconds,
) {

    init {
        require(barCount > 0) { "barCount must be positive" }
    }

    /** Cold: nothing is collected or emitted without a collector. */
    fun shape(level: Flow<Float>): Flow<List<Float>> = flow {
        // Starts awake so the first tick still emits the all-zero rest wave for a silent input.
        // Read by the tick loop, written only by the collector below; a tick already past the wait
        // when the wave comes to rest re-emits an unchanged wave, which distinctUntilChanged drops.
        val isAtRest = MutableStateFlow(false)
        val ticks = flow {
            while (true) {
                isAtRest.first { atRest -> !atRest }
                delay(interval)
                emit(WaveInput.Tick)
            }
        }
        val history = MutableList(barCount) { 0f }
        var currentLevel = 0f
        var windowMax = 0f
        val wave = merge(level.map(WaveInput::Level), ticks).transform { input ->
            when (input) {
                is WaveInput.Level -> {
                    currentLevel = input.value.coerceIn(0f, 1f)
                    windowMax = maxOf(windowMax, currentLevel)
                    if (currentLevel > 0f) isAtRest.value = false
                }
                WaveInput.Tick -> {
                    history.removeAt(history.lastIndex)
                    history.add(0, windowMax)
                    windowMax = currentLevel
                    emit(history.toList())
                    isAtRest.value = currentLevel == 0f && history.all { bar -> bar == 0f }
                }
            }
        }
        emitAll(wave.distinctUntilChanged())
    }

    private sealed interface WaveInput {
        data class Level(val value: Float) : WaveInput
        data object Tick : WaveInput
    }
}
