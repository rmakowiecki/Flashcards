package com.rossomak.flashcards.core.voice

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest

/**
 * Shapes a scalar input level into an outward-traveling wave of [barCount] bar levels, index 0
 * newest. Every [interval] it takes the maximum level seen in that window, so short plosives
 * between samples are not lost, and shifts it into index 0.
 *
 * The interval is the wave's propagation speed; consumers animate between snapshots over the same
 * interval.
 */
class VoiceLevelWaveShaper(
    private val barCount: Int = DEFAULT_BAR_COUNT,
    private val interval: Duration = DEFAULT_INTERVAL,
) {

    init {
        require(barCount > 0) { "barCount must be positive" }
    }

    private val restLevels: List<Float> = List(barCount) { 0f }

    /**
     * Cold: nothing is collected or emitted without a collector. Emits an all-zero list whenever
     * [isActive] turns false, and nothing more until it turns true again.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun shape(level: Flow<Float>, isActive: Flow<Boolean>): Flow<List<Float>> =
        isActive.distinctUntilChanged().transformLatest { active ->
            if (active) emitAll(wave(level)) else emit(restLevels)
        }

    private fun wave(level: Flow<Float>): Flow<List<Float>> {
        val ticks = flow {
            while (true) {
                delay(interval)
                emit(WaveInput.Tick)
            }
        }
        val history = restLevels.toMutableList()
        var currentLevel = 0f
        var windowMax = 0f
        return merge(level.map(WaveInput::Level), ticks).transform { input ->
            when (input) {
                is WaveInput.Level -> {
                    currentLevel = input.value.coerceIn(0f, 1f)
                    windowMax = maxOf(windowMax, currentLevel)
                }
                WaveInput.Tick -> {
                    history.removeAt(history.lastIndex)
                    history.add(0, windowMax)
                    windowMax = currentLevel
                    emit(history.toList())
                }
            }
        }
    }

    private sealed interface WaveInput {
        data class Level(val value: Float) : WaveInput
        data object Tick : WaveInput
    }

    companion object {
        const val DEFAULT_BAR_COUNT = 5
        val DEFAULT_INTERVAL: Duration = 70.milliseconds
    }
}
