package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.RecentSessionEntryDto
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudyMode.Fast
import com.rossomak.flashcards.core.domain.model.StudyMode.Rated

/** `null` when [RecentSessionEntryDto.studyMode] or [RecentSessionEntryDto.sourceType] is unknown, or the entry's own-mode delivery flag is missing. */
fun RecentSessionEntryDto.toDomainOrNull(): RecentSession? {
    val mode = runCatching { StudyMode.valueOf(studyMode) }.getOrNull() ?: return null
    val source = runCatching { SessionSourceType.valueOf(sourceType) }.getOrNull() ?: return null
    val startedAt = startTimestamp.toInstant()
    return when (mode) {
        Rated -> voiceAnswering?.let { voiceAnsweringEnabled ->
            RecentSession.Rated(
                id = sessionId,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                sourceType = source,
                categoryId = categoryId,
                categoryName = categoryName,
                subcategoryIds = subcategoryIds,
                subcategoryNames = subcategoryNames,
                studiedCount = cardCount,
                xpTotal = xpTotal,
                voiceAnsweringEnabled = voiceAnsweringEnabled,
            )
        }
        Fast -> readAloud?.let { readAloudEnabled ->
            RecentSession.Fast(
                id = sessionId,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                sourceType = source,
                categoryId = categoryId,
                categoryName = categoryName,
                subcategoryIds = subcategoryIds,
                subcategoryNames = subcategoryNames,
                studiedCount = cardCount,
                xpTotal = xpTotal,
                readAloudEnabled = readAloudEnabled,
            )
        }
    }
}
