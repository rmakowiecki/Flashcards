package com.rossomak.flashcards.core.data.source

import com.google.firebase.functions.FirebaseFunctions
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Wraps `httpsCallable("submitStudySession")`. the endpoint resolves from the initialized `FirebaseApp`
 * (`google-services.json`), not a base URL, and the caller's Firebase ID token attaches
 * automatically — no Retrofit, no `Authorization` header to manage by hand.
 *
 * The wire payload's field names mirror `functions/src/lib/submitStudySession.ts`'s own
 * `ValidatedSubmitStudySessionRequest` shape 1:1 — this is the one seam where these contracts must agree
 * across languages with no compiler to enforce it.
 *
 * The response is read into a [SessionScore]. Any missing or malformed field (the Rated-only counts
 * excepted) makes the whole response unreadable: that is logged and returned as success with a `null`
 * score. The call itself succeeded, so the server has recorded the session, and retrying would only
 * replay the same unreadable answer and block every later queue entry.
 *
 * A field added to [SessionResult] needs updating here (`toPayload()`, this class's own network-wire
 * subset) **and independently** in [com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto]
 * / [com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper] for the durable queue's
 * full-fidelity shape — see that DTO's own class doc for why the two are deliberately not derived from
 * one another.
 */
class FirebaseSessionSubmissionRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) : SessionSubmissionRemoteDataSource {

    // Broad on purpose - a callable Task can fail with more than
    // just FirebaseFunctionsException (a transport-layer error before the SDK wraps it, say), and
    // this call site has no surrounding try/catch of its own — narrowing this would let an
    // unanticipated exception type crash instead of surfacing as Result.failure.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun submitSession(ownerUid: String, sessionResult: SessionResult): Result<SessionScore?> = withContext(Dispatchers.IO) {
        val response = try {
            functions.getHttpsCallable(SUBMIT_STUDY_SESSION_FUNCTION_NAME).call(sessionResult.toPayload(ownerUid)).await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return@withContext Result.failure(exception)
        }
        try {
            Result.success(parseSessionScore(response.getData()))
        } catch (exception: IllegalArgumentException) {
            logw(exception) { "Unreadable submitStudySession response for session ${sessionResult.id}, treating it as delivered without a score" }
            Result.success(null)
        }
    }

    private fun SessionResult.toPayload(ownerUid: String): Map<String, Any> = buildMap {
        put(FIELD_OWNER_UID, ownerUid)
        put(FIELD_SESSION_ID, id)
        put(FIELD_STUDY_MODE, mode.name)
        put(FIELD_STARTED_AT_EPOCH_MILLIS, startedAt.toEpochMilli())
        put(FIELD_DURATION_SECONDS, durationSeconds)
        put(FIELD_ABANDONED, abandoned)
        put(FIELD_CATEGORY_ID, categoryId)
        put(FIELD_CATEGORY_NAME, categoryName)
        put(FIELD_SUBCATEGORY_IDS, subcategoryIds)
        put(FIELD_SUBCATEGORY_NAMES, subcategoryNames)
        put(FIELD_SOURCE_TYPE, sourceType.name)
        put(FIELD_CARD_RESULTS, cardResults.map { entry -> entry.toPayload() })
        put(FIELD_STUDY_DATE, studyDate)
        put(FIELD_STUDY_DATE_UTC_OFFSET_MINUTES, studyDateUtcOffsetMinutes)
        put(FIELD_DAILY_GOAL_MINUTES, dailyGoalMinutes)
        when (this@toPayload) {
            is SessionResult.Rated -> put(FIELD_VOICE_ANSWERING, voiceAnsweringEnabled)
            is SessionResult.Fast -> put(FIELD_READ_ALOUD, readAloudEnabled)
        }
    }

    private fun FlashcardResult.toPayload(): Map<String, Any> = buildMap {
        put(FIELD_CARD_ID, cardId)
        put(FIELD_CARD_SUBCATEGORY_ID, subcategoryId)
        put(FIELD_STATE, state.name)
        if (this@toPayload is FlashcardResult.Rated) {
            put(FIELD_ATTEMPTS_USED, attemptsUsed)
            put(FIELD_WAS_PREVIOUSLY_MASTERED, wasPreviouslyMastered)
        }
    }

    /** Throws [IllegalArgumentException] when a required field is missing or has the wrong type. */
    private fun parseSessionScore(data: Any?): SessionScore {
        val response = requireMap(data, "response")
        return SessionScore(
            breakdown = parseBreakdown(requireMap(response[FIELD_BREAKDOWN], FIELD_BREAKDOWN)),
            level = requireInt(response, FIELD_LEVEL),
            xpIntoCurrentLevel = requireLong(response, FIELD_XP_INTO_CURRENT_LEVEL),
            xpForNextLevel = requireLong(response, FIELD_XP_FOR_NEXT_LEVEL),
            levelsCrossed = requireList(response[FIELD_LEVELS_CROSSED], FIELD_LEVELS_CROSSED).map { level ->
                requireNotNull((level as? Number)?.toInt()) { "Non-numeric entry in $FIELD_LEVELS_CROSSED" }
            },
            levelBefore = requireInt(response, FIELD_LEVEL_BEFORE),
            xpIntoCurrentLevelBefore = requireLong(response, FIELD_XP_INTO_CURRENT_LEVEL_BEFORE),
            xpForNextLevelBefore = requireLong(response, FIELD_XP_FOR_NEXT_LEVEL_BEFORE),
            currentStreak = requireInt(response, FIELD_CURRENT_STREAK),
            counts = parseCounts(requireMap(response[FIELD_COUNTS], FIELD_COUNTS)),
            rates = parseRates(requireMap(response[FIELD_RATES], FIELD_RATES)),
        )
    }

    private fun parseBreakdown(breakdown: Map<*, *>): XpBreakdown = XpBreakdown(
        newCards = requireInt(breakdown, FIELD_NEW_CARDS),
        mastered = requireInt(breakdown, FIELD_MASTERED),
        partial = requireInt(breakdown, FIELD_PARTIAL),
        masteryDefenseBonus = requireInt(breakdown, FIELD_MASTERY_DEFENSE_BONUS),
        demastered = requireInt(breakdown, FIELD_DEMASTERED),
        timeStudied = requireInt(breakdown, FIELD_TIME_STUDIED),
        sessionCompletionBonus = requireInt(breakdown, FIELD_SESSION_COMPLETION_BONUS),
        dailyGoalBonus = requireInt(breakdown, FIELD_DAILY_GOAL_BONUS),
        streakBonus = requireInt(breakdown, FIELD_STREAK_BONUS),
    )

    private fun parseCounts(counts: Map<*, *>): SessionScoreCounts = SessionScoreCounts(
        newCardsStudied = requireInt(counts, FIELD_NEW_CARDS_STUDIED),
        newlyMastered = optionalInt(counts, FIELD_NEWLY_MASTERED),
        partial = optionalInt(counts, FIELD_PARTIAL),
        defended = optionalInt(counts, FIELD_DEFENDED),
        demastered = optionalInt(counts, FIELD_DEMASTERED),
    )

    private fun parseRates(rates: Map<*, *>): SessionScoreRates = SessionScoreRates(
        newCardStudied = requireInt(rates, FIELD_RATE_NEW_CARD_STUDIED),
        cardMastered = requireInt(rates, FIELD_RATE_CARD_MASTERED),
        cardPartial = requireInt(rates, FIELD_RATE_CARD_PARTIAL),
        masteryDefended = requireInt(rates, FIELD_RATE_MASTERY_DEFENDED),
        cardDemastered = requireInt(rates, FIELD_RATE_CARD_DEMASTERED),
        minuteStudied = requireInt(rates, FIELD_RATE_MINUTE_STUDIED),
        sessionCompleted = requireInt(rates, FIELD_RATE_SESSION_COMPLETED),
    )

    private fun requireMap(value: Any?, name: String): Map<*, *> = requireNotNull(value as? Map<*, *>) { "Missing or non-object $name" }

    private fun requireList(value: Any?, name: String): List<*> = requireNotNull(value as? List<*>) { "Missing or non-list $name" }

    private fun requireInt(fields: Map<*, *>, name: String): Int = requireNotNull(optionalInt(fields, name)) { "Missing or non-numeric $name" }

    private fun requireLong(fields: Map<*, *>, name: String): Long =
        requireNotNull((fields[name] as? Number)?.toLong()) { "Missing or non-numeric $name" }

    private fun optionalInt(fields: Map<*, *>, name: String): Int? = (fields[name] as? Number)?.toInt()

    private companion object {
        const val SUBMIT_STUDY_SESSION_FUNCTION_NAME = "submitStudySession"

        // Mirrors SubmitStudySessionResult's field names in functions/src/lib/submitStudySession.ts.
        const val FIELD_BREAKDOWN = "breakdown"
        const val FIELD_LEVEL = "level"
        const val FIELD_XP_INTO_CURRENT_LEVEL = "xpIntoCurrentLevel"
        const val FIELD_XP_FOR_NEXT_LEVEL = "xpForNextLevel"
        const val FIELD_LEVELS_CROSSED = "levelsCrossed"
        const val FIELD_LEVEL_BEFORE = "levelBefore"
        const val FIELD_XP_INTO_CURRENT_LEVEL_BEFORE = "xpIntoCurrentLevelBefore"
        const val FIELD_XP_FOR_NEXT_LEVEL_BEFORE = "xpForNextLevelBefore"
        const val FIELD_CURRENT_STREAK = "currentStreak"
        const val FIELD_COUNTS = "counts"
        const val FIELD_RATES = "rates"
        const val FIELD_NEW_CARDS = "newCards"
        const val FIELD_MASTERED = "mastered"
        const val FIELD_PARTIAL = "partial"
        const val FIELD_MASTERY_DEFENSE_BONUS = "masteryDefenseBonus"
        const val FIELD_DEMASTERED = "demastered"
        const val FIELD_TIME_STUDIED = "timeStudied"
        const val FIELD_SESSION_COMPLETION_BONUS = "sessionCompletionBonus"
        const val FIELD_DAILY_GOAL_BONUS = "dailyGoalBonus"
        const val FIELD_STREAK_BONUS = "streakBonus"
        const val FIELD_NEW_CARDS_STUDIED = "newCardsStudied"
        const val FIELD_NEWLY_MASTERED = "newlyMastered"
        const val FIELD_DEFENDED = "defended"
        const val FIELD_RATE_NEW_CARD_STUDIED = "newCardStudied"
        const val FIELD_RATE_CARD_MASTERED = "cardMastered"
        const val FIELD_RATE_CARD_PARTIAL = "cardPartial"
        const val FIELD_RATE_MASTERY_DEFENDED = "masteryDefended"
        const val FIELD_RATE_CARD_DEMASTERED = "cardDemastered"
        const val FIELD_RATE_MINUTE_STUDIED = "minuteStudied"
        const val FIELD_RATE_SESSION_COMPLETED = "sessionCompleted"

        // Mirrors ValidatedSubmitStudySessionRequest's field names in functions/src/lib/submitStudySession.ts.
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_STUDY_MODE = "studyMode"
        const val FIELD_STARTED_AT_EPOCH_MILLIS = "startedAtEpochMillis"
        const val FIELD_DURATION_SECONDS = "durationSeconds"
        const val FIELD_ABANDONED = "abandoned"
        const val FIELD_CATEGORY_ID = "categoryId"
        const val FIELD_CATEGORY_NAME = "categoryName"
        const val FIELD_SUBCATEGORY_IDS = "subcategoryIds"
        const val FIELD_SUBCATEGORY_NAMES = "subcategoryNames"
        const val FIELD_SOURCE_TYPE = "sourceType"
        const val FIELD_VOICE_ANSWERING = "voiceAnswering"
        const val FIELD_READ_ALOUD = "readAloud"
        const val FIELD_CARD_RESULTS = "cardResults"
        const val FIELD_CARD_ID = "cardId"
        const val FIELD_CARD_SUBCATEGORY_ID = "subcategoryId"
        const val FIELD_STATE = "state"
        const val FIELD_ATTEMPTS_USED = "attemptsUsed"
        const val FIELD_WAS_PREVIOUSLY_MASTERED = "wasPreviouslyMastered"
        const val FIELD_STUDY_DATE = "studyDate"
        const val FIELD_STUDY_DATE_UTC_OFFSET_MINUTES = "studyDateUtcOffsetMinutes"
        const val FIELD_DAILY_GOAL_MINUTES = "dailyGoalMinutes"
    }
}
