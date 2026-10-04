package com.rossomak.flashcards.core.domain.model

import java.time.Instant

/** A past Study Session shown on Home. Carries ids only: names resolve live in [RecentItem]. */
sealed interface RecentSession {
    val id: String
    val startedAt: Instant
    val durationSeconds: Int
    val sourceType: SessionSourceType
    val categoryId: String
    val subcategoryIds: List<String> // For Quick, the sampled ones.
    val studiedCount: Int // Number of Flashcard Results; never zero.
    val xpTotal: Int // Negative when de-mastery losses outweigh gains.
    val mode: StudyMode
        get() = when (this) {
            is Rated -> StudyMode.Rated
            is Fast -> StudyMode.Fast
        }

    data class Rated(
        override val id: String,
        override val startedAt: Instant,
        override val durationSeconds: Int,
        override val sourceType: SessionSourceType,
        override val categoryId: String,
        override val subcategoryIds: List<String>,
        override val studiedCount: Int,
        override val xpTotal: Int,
        val voiceAnsweringEnabled: Boolean,
    ) : RecentSession

    data class Fast(
        override val id: String,
        override val startedAt: Instant,
        override val durationSeconds: Int,
        override val sourceType: SessionSourceType,
        override val categoryId: String,
        override val subcategoryIds: List<String>,
        override val studiedCount: Int,
        override val xpTotal: Int,
        val readAloudEnabled: Boolean,
    ) : RecentSession
}
