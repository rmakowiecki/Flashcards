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
        val payloadSlot = stubCallable(Tasks.forResult(mockk()))

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
        val payloadSlot = stubCallable(Tasks.forResult(mockk()))

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

    private companion object {
        const val SUBMIT_STUDY_SESSION_FUNCTION_NAME = "submitStudySession"
    }
}
