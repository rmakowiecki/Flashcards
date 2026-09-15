package com.rossomak.flashcards.core.data.source

import com.google.firebase.functions.FirebaseFunctions
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
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
    override suspend fun submitSession(sessionResult: SessionResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            functions.getHttpsCallable(SUBMIT_STUDY_SESSION_FUNCTION_NAME).call(sessionResult.toPayload()).await()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun SessionResult.toPayload(): Map<String, Any> = mapOf(
        FIELD_SESSION_ID to id,
        FIELD_STUDY_MODE to mode.name,
        FIELD_STARTED_AT_EPOCH_MILLIS to startedAt.toEpochMilli(),
        FIELD_DURATION_SECONDS to durationSeconds,
        FIELD_ABANDONED to abandoned,
        FIELD_CATEGORY_ID to categoryId,
        FIELD_CATEGORY_NAME to categoryName,
        FIELD_SUBCATEGORY_IDS to subcategoryIds,
        FIELD_SUBCATEGORY_NAMES to subcategoryNames,
        FIELD_CARD_RESULTS to cardResults.map { entry -> entry.toPayload() },
        FIELD_STUDY_DATE to studyDate,
        FIELD_STUDY_DATE_UTC_OFFSET_MINUTES to studyDateUtcOffsetMinutes,
        FIELD_DAILY_GOAL_MINUTES to dailyGoalMinutes,
    )

    private fun FlashcardResult.toPayload(): Map<String, Any> = buildMap {
        put(FIELD_CARD_ID, cardId)
        put(FIELD_CARD_SUBCATEGORY_ID, subcategoryId)
        put(FIELD_STATE, state.name)
        if (this@toPayload is FlashcardResult.Rated) {
            put(FIELD_ATTEMPTS_USED, attemptsUsed)
            put(FIELD_WAS_PREVIOUSLY_MASTERED, wasPreviouslyMastered)
        }
    }

    private companion object {
        const val SUBMIT_STUDY_SESSION_FUNCTION_NAME = "submitStudySession"

        // Mirrors ValidatedSubmitStudySessionRequest's field names in functions/src/lib/submitStudySession.ts.
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_STUDY_MODE = "studyMode"
        const val FIELD_STARTED_AT_EPOCH_MILLIS = "startedAtEpochMillis"
        const val FIELD_DURATION_SECONDS = "durationSeconds"
        const val FIELD_ABANDONED = "abandoned"
        const val FIELD_CATEGORY_ID = "categoryId"
        const val FIELD_CATEGORY_NAME = "categoryName"
        const val FIELD_SUBCATEGORY_IDS = "subcategoryIds"
        const val FIELD_SUBCATEGORY_NAMES = "subcategoryNames"
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
