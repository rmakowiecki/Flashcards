package com.rossomak.flashcards.core.domain.model

/**
 * One writable [UserPreferences] field per case, so a screen writes exactly the preference it
 * changed instead of a read-modify-write of the whole object.
 */
sealed interface UserPreference {
    data class DailyGoalMinutes(val value: Int) : UserPreference

    data class HasSeenOnboarding(val value: Boolean) : UserPreference

    data class HasSeenVoiceAnsweringInfo(val value: Boolean) : UserPreference

    /** The last flashcard-cache generation seed this device saw (ADR-0039). */
    data class CacheSeed(val value: Int) : UserPreference
}
