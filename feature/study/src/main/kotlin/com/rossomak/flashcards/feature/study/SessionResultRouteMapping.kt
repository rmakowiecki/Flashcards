package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.StudyMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * Flattens [this] onto [StudySessionSummaryRoute] for the terminal navigation event — see that
 * type's KDoc for why the [SessionResult.cardResults] fields carry a `card` prefix, why
 * [cardAttemptsUsed]/[cardWasPreviouslyMastered] are `null` for a [SessionResult.Fast] result, why
 * [SessionResult.startedAt] becomes a `Long`, and why [SessionResult.studyDateUtcOffsetMinutes] (captured
 * at session start by the caller, unlike [SessionResult.studyDate]/[SessionResult.dailyGoalMinutes],
 * which really are unread placeholders here) is the one of the three actually carried through.
 */
fun SessionResult.toSummaryRoute(): StudySessionSummaryRoute = StudySessionSummaryRoute(
    sessionId = id,
    mode = mode,
    startedAtEpochSecond = startedAt.epochSecond,
    studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
    durationSeconds = durationSeconds,
    abandoned = abandoned,
    categoryId = categoryId,
    categoryName = categoryName,
    subcategoryIds = subcategoryIds,
    subcategoryNames = subcategoryNames,
    cardIds = cardResults.map { it.cardId },
    cardSubcategoryIds = cardResults.map { it.subcategoryId },
    cardStates = cardResults.map { it.state },
    cardAttemptsUsed = when (this) {
        is SessionResult.Rated -> cardResults.map { it.attemptsUsed }
        is SessionResult.Fast -> null
    },
    cardWasPreviouslyMastered = when (this) {
        is SessionResult.Rated -> cardResults.map { it.wasPreviouslyMastered }
        is SessionResult.Fast -> null
    },
)

/**
 * The inverse of [toSummaryRoute] — how the Summary ViewModel reads the route back into a
 * [SessionResult]. [dailyGoalMinutes] is supplied by the caller — a fresh preferences read that has
 * no business surviving process death via `SavedStateHandle` the way the route's other fields do —
 * this mapping function stays pure either way. [SessionResult.studyDate] is derived here from
 * [StudySessionSummaryRoute.startedAtEpochSecond] shifted by [StudySessionSummaryRoute.studyDateUtcOffsetMinutes]
 * (mirroring the server's own `deriveLocalStudyDate`), rather than the caller re-deriving it from
 * [java.time.ZoneId.systemDefault] at submission time — that field is carried on the route precisely
 * so a device timezone change between session start and submission can't shift it.
 */
fun StudySessionSummaryRoute.toSessionResult(dailyGoalMinutes: Int): SessionResult {
    val studyDate = Instant.ofEpochSecond(startedAtEpochSecond)
        .plusSeconds(studyDateUtcOffsetMinutes * SECONDS_PER_MINUTE.toLong())
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
    return toSessionResult(studyDate = studyDate, dailyGoalMinutes = dailyGoalMinutes)
}

private const val SECONDS_PER_MINUTE = 60

private fun StudySessionSummaryRoute.toSessionResult(
    studyDate: String,
    dailyGoalMinutes: Int,
): SessionResult = when (mode) {
    StudyMode.Rated -> SessionResult.Rated(
        id = sessionId,
        startedAt = Instant.ofEpochSecond(startedAtEpochSecond),
        durationSeconds = durationSeconds,
        abandoned = abandoned,
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryNames,
        cardResults = cardIds.indices.map { index ->
            FlashcardResult.Rated(
                cardId = cardIds[index],
                subcategoryId = cardSubcategoryIds[index],
                state = cardStates[index],
                attemptsUsed = requireNotNull(cardAttemptsUsed) { "a Rated route always carries cardAttemptsUsed" }[index],
                wasPreviouslyMastered = requireNotNull(cardWasPreviouslyMastered) {
                    "a Rated route always carries cardWasPreviouslyMastered"
                }[index],
            )
        },
        studyDate = studyDate,
        studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
    )
    StudyMode.Fast -> SessionResult.Fast(
        id = sessionId,
        startedAt = Instant.ofEpochSecond(startedAtEpochSecond),
        durationSeconds = durationSeconds,
        abandoned = abandoned,
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryNames,
        cardResults = cardIds.indices.map { index ->
            FlashcardResult.Fast(
                cardId = cardIds[index],
                subcategoryId = cardSubcategoryIds[index],
                state = cardStates[index],
            )
        },
        studyDate = studyDate,
        studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
    )
}
