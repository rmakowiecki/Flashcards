package com.rossomak.flashcards.core.domain.model

/** Which half of the presented card the voice player is on. */
enum class VoicePhase { Question, Answer }

/**
 * The voice player's transport state, as the study session sees it. Carries only what the player
 * itself owns; every session rule reads it and never writes it.
 *
 * @param isActive `true` while the player holds a non-empty card list.
 * @param currentIndex the index the player is on, within the list it was last given.
 * @param isInBetweenPause the pause the player makes between one card's answer and the next
 * card's question. Only reached by a player that reads answers (a Fast read-aloud session).
 */
data class VoicePlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = 0,
    val totalCards: Int = 0,
    val phase: VoicePhase = VoicePhase.Question,
    val isInBetweenPause: Boolean = false,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
) {
    companion object {
        const val DEFAULT_SPEECH_RATE = 1f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2f
    }
}
