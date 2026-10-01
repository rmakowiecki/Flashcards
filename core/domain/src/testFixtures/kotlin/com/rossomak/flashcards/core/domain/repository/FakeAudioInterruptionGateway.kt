package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeAudioInterruptionGateway : AudioInterruptionGateway {

    private val signalChannel = Channel<AudioEnvironmentSignal>(Channel.UNLIMITED)
    override val signals: Flow<AudioEnvironmentSignal> = signalChannel.receiveAsFlow()

    fun emit(signal: AudioEnvironmentSignal) {
        signalChannel.trySend(signal)
    }
}
