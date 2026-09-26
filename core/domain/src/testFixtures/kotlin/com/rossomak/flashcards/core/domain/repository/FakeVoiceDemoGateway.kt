package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.Cancelled
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.Captured
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.NothingCaptured
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVoiceDemoGateway : VoiceDemoGateway {
    override val state = MutableStateFlow<VoiceDemoState>(VoiceDemoState.Idle)
    override val levels = MutableSharedFlow<List<Float>>()

    /** What [finishRecording] returns; the matching end state is applied alongside it. */
    var finishRecordingOutcome: VoiceDemoRecordingResult = Captured

    var startCount = 0
        private set
    var playCount = 0
        private set
    var stopCount = 0
        private set
    var finishRecordingCount = 0
        private set

    override fun start() {
        startCount++
    }

    override suspend fun finishRecording(): VoiceDemoRecordingResult {
        finishRecordingCount++
        when (finishRecordingOutcome) {
            Captured -> state.value = VoiceDemoState.Ready
            NothingCaptured -> state.value = VoiceDemoState.Idle
            Cancelled -> Unit
        }
        return finishRecordingOutcome
    }

    override fun play() {
        playCount++
    }

    override fun stop() {
        stopCount++
        state.value = VoiceDemoState.Idle
    }
}
