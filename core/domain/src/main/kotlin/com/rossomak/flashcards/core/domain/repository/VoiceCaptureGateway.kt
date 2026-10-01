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

    /** Holds the wake lock and watches the audio devices until [stopVoiceAnswering]. */
    fun startVoiceAnswering()

    /** Stops capture and releases everything, including what [prepareListening] prepared, and the wake lock. */
    fun stopVoiceAnswering()

    /**
     * Prepares the microphone for one answer and suspends until it can capture, for example while a
     * Bluetooth headset connects or reconnects.
     */
    suspend fun prepareListening()

    /**
     * Opens the microphone for one answer. [CaptureEvent.MicrophoneOpened] reports when it really
     * records.
     */
    fun startListening()

    /** Closes the microphone and releases what [prepareListening] prepared. */
    fun stopListening()

    /** Plays the short sound that tells the user the microphone records. */
    fun playListeningCue()

    /**
     * While [closed], the microphone keeps recording but drops every frame before it reaches speech
     * detection or the answer being captured, so a sound from another app never ends up in the
     * user's answer. The state outlives listening windows until the next call.
     */
    fun setCaptureGate(closed: Boolean)
}
