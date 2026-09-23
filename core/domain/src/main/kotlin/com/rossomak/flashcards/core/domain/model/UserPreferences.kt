package com.rossomak.flashcards.core.domain.model

/**
 * Device-scoped user preferences held in local storage, not Firestore. App/device-scoped
 * concerns only — session defaults live on [StudySessionPreferences] instead.
 *
 * [hasSeenOnboarding] is deliberately device-scoped rather than uid-scoped: signing a second
 * account into the same device skips onboarding, and the same account on a new device sees it
 * again. Accepted trade-off — see docs/design/onboarding-flow.md.
 *
 * [localCacheSeed] is nullable with no default, unlike every other field here: `null`
 * unambiguously means "never checked," which forces a mismatch against the server seed — and
 * therefore one refresh per Subcategory the user visits — on every device's first launch after
 * ADR-0039 shipped. Defaulting it to a real int risked a false-negative match against a
 * low-numbered server seed at ship time.
 */
data class UserPreferences(
    val hasSeenOnboarding: Boolean = false,
    val dailyGoalMinutes: Int = DailyGoal.DEFAULT_MINUTES,
    val hasSeenVoiceAnsweringInfo: Boolean = false,
    val localCacheSeed: Int? = null,
)
