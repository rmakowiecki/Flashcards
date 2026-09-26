package com.rossomak.flashcards.feature.study.voice

import com.rossomak.flashcards.core.domain.model.Flashcard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class VoicePhase { Question, Answer }

data class VoicePlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = 0,
    val totalCards: Int = 0,
    val phase: VoicePhase = VoicePhase.Question,
    val isInBetweenPause: Boolean = false,
    val isAwaitingSpokenAnswer: Boolean = false,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    val error: String? = null,
) {
    companion object {
        const val DEFAULT_SPEECH_RATE = 1f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2f
        const val REWIND_THRESHOLD_MS = 3_000L
    }
}

interface VoiceGateway {
    val state: StateFlow<VoicePlaybackState>
    val voiceAnswerState: StateFlow<VoiceAnswerState>

    /**
     * Raw microphone input level in `0..1` while voice answering listens, 0 otherwise, including
     * while the voice engine is not bound. Cold.
     */
    val rawVoiceLevel: Flow<Float>

    fun start(cards: List<Flashcard>, startIndex: Int, subcategoryName: String)

    /**
     * Swaps in a fresh queue order without touching playback — [cards]'s head is always whatever
     * is currently speaking, so the in-flight utterance is untouched; only what comes next changes.
     * Rated sessions call this after every rating or silence-timeout requeue (ADR-0046) instead of
     * re-calling [start], which would restart the engine from scratch.
     */
    fun updateQueue(cards: List<Flashcard>)
    fun stop()
    fun togglePlayPause()
    fun rewindToNext()
    fun rewindToPrevious()
    fun restartCurrentCard()
    fun showAnswer()
    fun setSpeechRate(rate: Float)
    fun setVoice(voiceId: String?)
    fun setVoiceAnswering(enabled: Boolean)

    /**
     * Tells the voice-answering pipeline whether *its own* next silence timeout — should one
     * fire before this is next called — is the one that pauses the session (ADR-0025's
     * consecutive-silence pause), so it can pick the right spoken notice on its own; the
     * consecutive-silence count itself stays owned by the caller.
     */
    fun setNextSilenceWillPauseSession(willPause: Boolean)
}
