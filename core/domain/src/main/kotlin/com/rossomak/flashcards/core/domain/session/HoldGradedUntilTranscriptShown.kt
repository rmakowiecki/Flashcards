package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * Holds back [VoiceAnswerGradingEvent.Graded] and [VoiceAnswerGradingEvent.Failed] — or an upstream
 * exception — until [VoiceAnswerGradingEvent.TranscriptReady] has been shown for at least
 * [minDisplay], so the user can confirm their speech was recognized before the grade replaces it.
 * There is no way back to the transcript afterwards. Held here rather than in the UI so the spoken
 * grade (or failure notice) and the visual one still start together.
 *
 * Without a preceding transcript, the grade and failures pass through immediately. Only upstream
 * exceptions are held; exceptions thrown downstream propagate untouched.
 */
fun Flow<VoiceAnswerGradingEvent>.holdGradedUntilTranscriptShown(
    minDisplay: Duration = MIN_TRANSCRIPT_DISPLAY,
    timeSource: TimeSource = TimeSource.Monotonic,
): Flow<VoiceAnswerGradingEvent> = flow {
    var transcriptShownAt: TimeMark? = null

    suspend fun holdTranscript() {
        transcriptShownAt?.let { shownAt -> delay(minDisplay - shownAt.elapsedNow()) }
    }

    emitAll(
        this@holdGradedUntilTranscriptShown
            .catch { error ->
                holdTranscript()
                throw error
            }
            .onEach { event ->
                when (event) {
                    is VoiceAnswerGradingEvent.TranscriptReady -> transcriptShownAt = timeSource.markNow()
                    is VoiceAnswerGradingEvent.Graded, is VoiceAnswerGradingEvent.Failed -> holdTranscript()
                }
            }
    )
}
