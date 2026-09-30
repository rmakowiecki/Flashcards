package com.rossomak.flashcards.core.data.model

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.StudyMode
import java.time.Instant

/** `toDto()`/`toDomain()` conversions between [SessionResult] and [PendingSessionSubmissionDto]. */
object PendingSessionSubmissionMapper {

    fun SessionResult.toDto(uid: String): PendingSessionSubmissionDto = PendingSessionSubmissionDto(
        id = id,
        uid = uid,
        mode = mode.name,
        startedAtEpochMillis = startedAt.toEpochMilli(),
        durationSeconds = durationSeconds,
        abandoned = abandoned,
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryNames,
        cardResults = cardResults.map { it.toDto() },
        studyDate = studyDate,
        dailyGoalMinutes = dailyGoalMinutes,
        studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
    )

    /**
     * Throws [IllegalArgumentException] for a malformed entry, including one with a blank
     * [PendingSessionSubmissionDto.uid]: no User owns it, so it can never be delivered.
     */
    fun PendingSessionSubmissionDto.toDomain(): SessionResult {
        require(uid.isNotBlank()) { "Pending session '$id' has no owning uid" }
        return toOwnedDomain()
    }

    private fun PendingSessionSubmissionDto.toOwnedDomain(): SessionResult = when (StudyMode.valueOf(mode)) {
        StudyMode.Rated -> SessionResult.Rated(
            id = id,
            startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
            durationSeconds = durationSeconds,
            abandoned = abandoned,
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            cardResults = cardResults.map { it.toRatedDomain() },
            studyDate = studyDate,
            dailyGoalMinutes = dailyGoalMinutes,
            studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        )
        StudyMode.Fast -> SessionResult.Fast(
            id = id,
            startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
            durationSeconds = durationSeconds,
            abandoned = abandoned,
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            cardResults = cardResults.map { it.toFastDomain() },
            studyDate = studyDate,
            dailyGoalMinutes = dailyGoalMinutes,
            studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        )
    }

    private fun FlashcardResult.toDto(): PendingFlashcardResultDto = PendingFlashcardResultDto(
        cardId = cardId,
        subcategoryId = subcategoryId,
        state = state.name,
        attemptsUsed = (this as? FlashcardResult.Rated)?.attemptsUsed,
        wasPreviouslyMastered = (this as? FlashcardResult.Rated)?.wasPreviouslyMastered,
    )

    private fun PendingFlashcardResultDto.toRatedDomain(): FlashcardResult.Rated = FlashcardResult.Rated(
        cardId = cardId,
        subcategoryId = subcategoryId,
        state = FlashcardStudyProgressState.valueOf(state),
        attemptsUsed = requireNotNull(attemptsUsed) { "Rated pending card result '$cardId' missing attemptsUsed" },
        wasPreviouslyMastered = requireNotNull(wasPreviouslyMastered) {
            "Rated pending card result '$cardId' missing wasPreviouslyMastered"
        },
    )

    private fun PendingFlashcardResultDto.toFastDomain(): FlashcardResult.Fast = FlashcardResult.Fast(
        cardId = cardId,
        subcategoryId = subcategoryId,
        state = FlashcardStudyProgressState.valueOf(state),
    )
}
