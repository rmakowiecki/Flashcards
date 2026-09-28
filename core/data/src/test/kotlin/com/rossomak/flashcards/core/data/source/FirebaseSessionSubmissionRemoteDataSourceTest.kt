package com.rossomak.flashcards.core.data.source

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FirebaseSessionSubmissionRemoteDataSourceTest {

    private val functions: FirebaseFunctions = mockk()
    private val callableReference: HttpsCallableReference = mockk()

    private fun createApi(): FirebaseSessionSubmissionRemoteDataSource = FirebaseSessionSubmissionRemoteDataSource(functions)

    private fun stubCallable(result: Task<HttpsCallableResult>): CapturingSlot<Any> {
        val payloadSlot = slot<Any>()
        every { functions.getHttpsCallable(SUBMIT_STUDY_SESSION_FUNCTION_NAME) } returns callableReference
        every { callableReference.call(capture(payloadSlot)) } returns result
        return payloadSlot
    }

    private fun callableResult(data: Any?): HttpsCallableResult {
        val result: HttpsCallableResult = mockk()
        every { result.getData() } returns data
        return result
    }

    private fun ratedSessionResult(): SessionResult.Rated = SessionResult.Rated(
        id = "session-1",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        cardResults = listOf(
            FlashcardResult.Rated(
                cardId = "card-1",
                subcategoryId = "sub-1",
                state = FlashcardStudyProgressState.Mastered,
                attemptsUsed = 1,
                wasPreviouslyMastered = false,
            ),
        ),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = -300,
    )

    private fun fastSessionResult(): SessionResult.Fast = SessionResult.Fast(
        id = "session-2",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 30,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        cardResults = listOf(FlashcardResult.Fast(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen)),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = -300,
    )

    @Test
    fun `submitSession calls the submitStudySession callable with a Rated payload matching the function's field names`() = runTest {
        val session = ratedSessionResult()
        val cardResult = session.cardResults.single()
        val payloadSlot = stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE)))

        val result = createApi().submitSession(session)

        result.isSuccess shouldBe true
        @Suppress("UNCHECKED_CAST")
        val payload = payloadSlot.captured as Map<String, Any>
        payload["sessionId"] shouldBe session.id
        payload["studyMode"] shouldBe session.mode.name
        payload["durationSeconds"] shouldBe session.durationSeconds
        payload["abandoned"] shouldBe session.abandoned
        payload["categoryId"] shouldBe session.categoryId
        payload["categoryName"] shouldBe session.categoryName
        payload["subcategoryIds"] shouldBe session.subcategoryIds
        payload["subcategoryNames"] shouldBe session.subcategoryNames
        payload["studyDate"] shouldBe session.studyDate
        payload["studyDateUtcOffsetMinutes"] shouldBe session.studyDateUtcOffsetMinutes
        payload["dailyGoalMinutes"] shouldBe session.dailyGoalMinutes

        @Suppress("UNCHECKED_CAST")
        val payloadCardResult = (payload["cardResults"] as List<Map<String, Any>>).single()
        payloadCardResult["cardId"] shouldBe cardResult.cardId
        payloadCardResult["subcategoryId"] shouldBe cardResult.subcategoryId
        payloadCardResult["state"] shouldBe cardResult.state.name
        payloadCardResult["attemptsUsed"] shouldBe cardResult.attemptsUsed
        payloadCardResult["wasPreviouslyMastered"] shouldBe cardResult.wasPreviouslyMastered
    }

    @Test
    fun `submitSession omits attemptsUsed and wasPreviouslyMastered for a Fast card result`() = runTest {
        val session = fastSessionResult()
        val payloadSlot = stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE)))

        createApi().submitSession(session)

        @Suppress("UNCHECKED_CAST")
        val payload = payloadSlot.captured as Map<String, Any>
        payload["studyMode"] shouldBe session.mode.name
        @Suppress("UNCHECKED_CAST")
        val payloadCardResult = (payload["cardResults"] as List<Map<String, Any>>).single()
        payloadCardResult.keys shouldBe setOf("cardId", "subcategoryId", "state")
        payloadCardResult["state"] shouldBe session.cardResults.single().state.name
    }

    @Test
    fun `submitSession wraps a callable failure in a failure result`() = runTest {
        val error: FirebaseFunctionsException = mockk()
        stubCallable(Tasks.forException(error))

        val result = createApi().submitSession(ratedSessionResult())

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        verify(exactly = 1) { callableReference.call(any()) }
    }

    @Test
    fun `submitSession reads a Rated response into the server's score`() = runTest {
        stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE)))

        val result = createApi().submitSession(ratedSessionResult())

        result shouldBe Result.success(
            SessionScore(
                breakdown = XpBreakdown(
                    newCards = 10,
                    mastered = 100,
                    partial = 0,
                    masteryDefenseBonus = 0,
                    demastered = 0,
                    timeStudied = 10,
                    sessionCompletionBonus = 500,
                    dailyGoalBonus = 1000,
                    streakBonus = 250,
                ),
                level = 2,
                xpIntoCurrentLevel = 870L,
                xpForNextLevel = 6000L,
                levelsCrossed = listOf(2),
                counts = SessionScoreCounts(newCardsStudied = 1, newlyMastered = 1, partial = 0, defended = 0, demastered = 0),
                rates = SessionScoreRates(
                    newCardStudied = 10,
                    cardMastered = 100,
                    cardPartial = 25,
                    masteryDefended = 50,
                    cardDemastered = -80,
                    minuteStudied = 10,
                    sessionCompleted = 500,
                ),
            ),
        )
    }

    @Test
    fun `submitSession reads a Fast response without Rated-only counts`() = runTest {
        stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE + (FIELD_COUNTS to mapOf("newCardsStudied" to 2)))))

        val score = createApi().submitSession(fastSessionResult()).getOrThrow().shouldNotBeNull()

        score.counts shouldBe SessionScoreCounts(newCardsStudied = 2, newlyMastered = null, partial = null, defended = null, demastered = null)
    }

    @Test
    fun `submitSession reads a response without counts or rates, leaving both null`() = runTest {
        stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE - FIELD_COUNTS - FIELD_RATES)))

        val score = createApi().submitSession(ratedSessionResult()).getOrThrow().shouldNotBeNull()

        score.counts shouldBe null
        score.rates shouldBe null
    }

    @Test
    fun `submitSession succeeds without a score on a response missing a required field`() = runTest {
        stubCallable(Tasks.forResult(callableResult(RATED_RESPONSE - "level")))

        val result = createApi().submitSession(ratedSessionResult())

        result shouldBe Result.success(null)
    }

    @Test
    fun `submitSession succeeds without a score on a response that is not an object`() = runTest {
        stubCallable(Tasks.forResult(callableResult(null)))

        val result = createApi().submitSession(ratedSessionResult())

        result shouldBe Result.success(null)
    }

    private companion object {
        const val SUBMIT_STUDY_SESSION_FUNCTION_NAME = "submitStudySession"
        const val FIELD_COUNTS = "counts"
        const val FIELD_RATES = "rates"

        // Shaped as the Functions SDK decodes JSON: small numbers as Int, lists and objects as List and Map.
        val RATED_RESPONSE: Map<String, Any> = mapOf(
            "breakdown" to mapOf(
                "newCards" to 10,
                "mastered" to 100,
                "partial" to 0,
                "masteryDefenseBonus" to 0,
                "demastered" to 0,
                "timeStudied" to 10,
                "sessionCompletionBonus" to 500,
                "dailyGoalBonus" to 1000,
                "streakBonus" to 250,
                "xpTotal" to 1870,
            ),
            "level" to 2,
            "xpIntoCurrentLevel" to 870,
            "xpForNextLevel" to 6000,
            "levelsCrossed" to listOf(2),
            "durationSeconds" to 60,
            FIELD_COUNTS to mapOf("newCardsStudied" to 1, "newlyMastered" to 1, "partial" to 0, "defended" to 0, "demastered" to 0),
            FIELD_RATES to mapOf(
                "newCardStudied" to 10,
                "cardMastered" to 100,
                "cardPartial" to 25,
                "masteryDefended" to 50,
                "cardDemastered" to -80,
                "minuteStudied" to 10,
                "sessionCompleted" to 500,
            ),
        )
    }
}
