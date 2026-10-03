package com.rossomak.flashcards.core.data.model

import com.google.firebase.Timestamp

/** The `users/{uid}/recents/state` document; a missing document reads as no entries. */
data class RecentsStateDto(
    val entries: List<RecentSessionEntryDto> = emptyList(),
)

/**
 * One entry of `recents/state.entries`, written only by `submitStudySession`. [voiceAnswering] is
 * present only on a Rated entry and [readAloud] only on a Fast one.
 */
data class RecentSessionEntryDto(
    val sessionId: String,
    val startTimestamp: Timestamp,
    val durationSeconds: Int,
    val studyMode: String,
    val voiceAnswering: Boolean?,
    val readAloud: Boolean?,
    val sourceType: String,
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val cardCount: Int,
    val xpTotal: Int,
)
