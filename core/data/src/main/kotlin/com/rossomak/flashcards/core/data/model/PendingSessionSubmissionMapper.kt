package com.rossomak.flashcards.core.data.model

import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.XpConfig
import java.time.Instant
import java.time.ZoneId

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
        xpConfig = xpConfig.toDto(),
    )

    /**
     * A blank [PendingSessionSubmissionDto.studyDate] or non-positive
     * [PendingSessionSubmissionDto.dailyGoalMinutes] means this entry was queued by an app version
     * that predates those fields (see that DTO's own doc) — migrate both sentinels here, once, so the
     * resulting [SessionResult] passes `submitStudySession`'s validation instead of being permanently
     * rejected and dead-lettered by [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker].
     * [studyDate] is derived from [PendingSessionSubmissionDto.startedAtEpochMillis] in the device's
     * *current* default zone — the original capture-time zone is not itself persisted, so this is a
     * best-effort reconstruction, not a guaranteed match of what the Summary ViewModel would have
     * computed at the time.
     *
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
            studyDate = migratedStudyDate(),
            dailyGoalMinutes = migratedDailyGoalMinutes(),
            studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
            xpConfig = xpConfig.toDomain(),
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
            studyDate = migratedStudyDate(),
            dailyGoalMinutes = migratedDailyGoalMinutes(),
            studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
            xpConfig = xpConfig.toDomain(),
        )
    }

    private fun PendingSessionSubmissionDto.migratedStudyDate(): String = studyDate.ifBlank {
        Instant.ofEpochMilli(startedAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    private fun PendingSessionSubmissionDto.migratedDailyGoalMinutes(): Int =
        if (dailyGoalMinutes > 0) dailyGoalMinutes else DailyGoal.DEFAULT_MINUTES

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

    private fun XpConfig.toDto(): PendingXpConfigDto = PendingXpConfigDto(
        newCardStudied = newCardStudied,
        cardMastered = cardMastered,
        cardPartial = cardPartial,
        masteryDefended = masteryDefended,
        cardDemastered = cardDemastered,
        sessionCompleted = sessionCompleted,
        dailyGoalMet = dailyGoalMet,
        streakPerDay = streakPerDay,
        streakMaxPerDay = streakMaxPerDay,
        minuteStudied = minuteStudied,
        levelCurveBase = levelCurveBase,
        levelCurveExponent = levelCurveExponent,
    )

    private fun PendingXpConfigDto.toDomain(): XpConfig = XpConfig(
        newCardStudied = newCardStudied,
        cardMastered = cardMastered,
        cardPartial = cardPartial,
        masteryDefended = masteryDefended,
        cardDemastered = cardDemastered,
        sessionCompleted = sessionCompleted,
        dailyGoalMet = dailyGoalMet,
        streakPerDay = streakPerDay,
        streakMaxPerDay = streakMaxPerDay,
        minuteStudied = minuteStudied,
        levelCurveBase = levelCurveBase,
        levelCurveExponent = levelCurveExponent,
    )
}
