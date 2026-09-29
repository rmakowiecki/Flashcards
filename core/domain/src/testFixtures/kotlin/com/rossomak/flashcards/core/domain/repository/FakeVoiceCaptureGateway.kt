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

    /** When set, [awaitRouteReady] suspends until it completes, like a Bluetooth route still settling. */
    var routeReadyGate: CompletableDeferred<Unit>? = null

    var isVoiceAnsweringStarted = false
        private set
    var startVoiceAnsweringCount = 0
        private set
    var stopVoiceAnsweringCount = 0
        private set
    var isListening = false
        private set
    var startListeningCount = 0
        private set

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

    override suspend fun awaitRouteReady() {
        routeReadyGate?.await()
    }

    override fun startListening() {
        startListeningCount++
        isListening = true
    }

    override fun stopListening() {
        isListening = false
    }
}
