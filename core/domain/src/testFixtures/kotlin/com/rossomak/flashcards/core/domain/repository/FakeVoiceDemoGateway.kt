package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVoiceDemoGateway : VoiceDemoGateway {
    override val state = MutableStateFlow<VoiceDemoState>(VoiceDemoState.Idle)

    var startCount = 0
        private set
    var playCount = 0
        private set
    var stopCount = 0
        private set

    override fun start() {
        startCount++
    }

    override fun play() {
        playCount++
    }

    override fun stop() {
        stopCount++
        state.value = VoiceDemoState.Idle
    }
}
