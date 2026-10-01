package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.CaptureEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeVoiceCaptureGateway : VoiceCaptureGateway {

    private val eventChannel = Channel<CaptureEvent>(Channel.UNLIMITED)
    override val captureEvents: Flow<CaptureEvent> = eventChannel.receiveAsFlow()

    /** Drops an emission while nothing collects it. */
    override val rawVoiceLevel = MutableSharedFlow<Float>()

    /** When set, [prepareListening] suspends until it completes, like a Bluetooth headset still connecting. */
    var routeReadyGate: CompletableDeferred<Unit>? = null

    /** When `false`, [startListening] does not report [CaptureEvent.MicrophoneOpened], like a microphone that never opens. */
    var reportsMicrophoneOpened = true

    var isVoiceAnsweringStarted = false
        private set
    var startVoiceAnsweringCount = 0
        private set
    var stopVoiceAnsweringCount = 0
        private set
    var isListening = false
        private set
    var prepareListeningCount = 0
        private set
    var startListeningCount = 0
        private set
    var stopListeningCount = 0
        private set
    var listeningCueCount = 0
        private set

    /** Whether the capture gate is closed now. */
    var isCaptureGateClosed = false
        private set

    /** Every value [setCaptureGate] was called with, in order. */
    val captureGateHistory = mutableListOf<Boolean>()

    fun emit(event: CaptureEvent) {
        eventChannel.trySend(event)
    }

    override fun startVoiceAnswering() {
        startVoiceAnsweringCount++
        isVoiceAnsweringStarted = true
    }

    override fun stopVoiceAnswering() {
        stopVoiceAnsweringCount++
        isVoiceAnsweringStarted = false
        isListening = false
    }

    override suspend fun prepareListening() {
        prepareListeningCount++
        routeReadyGate?.await()
    }

    override fun startListening() {
        startListeningCount++
        isListening = true
        if (reportsMicrophoneOpened) emit(CaptureEvent.MicrophoneOpened)
    }

    override fun stopListening() {
        stopListeningCount++
        isListening = false
    }

    override fun playListeningCue() {
        listeningCueCount++
    }

    override fun setCaptureGate(closed: Boolean) {
        isCaptureGateClosed = closed
        captureGateHistory += closed
    }
}
