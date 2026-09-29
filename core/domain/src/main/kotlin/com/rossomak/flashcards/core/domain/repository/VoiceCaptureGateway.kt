package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.CaptureEvent
import kotlinx.coroutines.flow.Flow

/**
 * The study session's microphone: route, wake lock and capture. It makes no session decision and
 * never checks the microphone permission; the caller does, before [startVoiceAnswering].
 */
interface VoiceCaptureGateway {

    /** Unconflated, single collector. Only reports while voice answering is started. */
    val captureEvents: Flow<CaptureEvent>

    /**
     * Raw microphone input level in `0..1` while listening, 0 otherwise, including while the voice
     * stack is not running. Computed on the device and never logged, stored or uploaded.
     */
    val rawVoiceLevel: Flow<Float>

    /** Holds the wake lock and the session's microphone route until [stopVoiceAnswering]. */
    fun startVoiceAnswering()

    /** Stops capture and releases the route and the wake lock. */
    fun stopVoiceAnswering()

    /** Suspends until the microphone route can capture, for example while a Bluetooth headset reconnects. */
    suspend fun awaitRouteReady()

    /** Opens the microphone for one answer. */
    fun startListening()
    fun stopListening()
}
