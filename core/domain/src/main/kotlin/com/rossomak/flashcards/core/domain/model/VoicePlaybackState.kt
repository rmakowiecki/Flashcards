package com.rossomak.flashcards.core.domain.model

/**
 * The voice player's transport state, as the study session sees it. Carries only what the player
 * itself owns; every session rule reads it and never writes it. The presented card and part belong
 * to the session coordinator, which tells the player what to present.
 *
 * @param isActive `true` while the player holds a non-empty card list.
 */
data class VoicePlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
)
