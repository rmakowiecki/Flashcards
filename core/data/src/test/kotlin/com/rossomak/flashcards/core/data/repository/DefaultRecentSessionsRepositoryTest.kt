package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.model.RecentSessionEntryDto
import com.rossomak.flashcards.core.data.model.RecentsStateDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.RecentsRemoteDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRecentSessionsRepositoryTest {

    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()
    private val xpConfigRepository = FakeXpConfigRepository().apply { resultToReturn = Result.success(CONFIG) }
    private val remoteRecents = MutableStateFlow(RecentsStateDto())
    private val remoteDataSource: RecentsRemoteDataSource = mockk { every { observeRecents() } returns remoteRecents }

    private fun createRepository() = DefaultRecentSessionsRepository(
        remoteDataSource,
        PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, scoringStateRemoteDataSource, xpConfigRepository),
    )

    @Before
    fun setUp() {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(any()) } returns null
    }

    @Test
    fun `server entries map to Recents, newest first`() = runTest {
        remoteRecents.value = RecentsStateDto(
            entries = listOf(
                remoteEntry(SESSION_ONE_ID, SESSION_ONE_START),
                remoteEntry(SESSION_TWO_ID, SESSION_TWO_START).copy(studyMode = "Fast", voiceAnswering = null, readAloud = true, sourceType = Custom.name),
            ),
        )

        createRepository().observeRecentSessions().test {
            awaitItem() shouldBe listOf(
                RecentSession.Fast(
                    id = SESSION_TWO_ID,
                    startedAt = SESSION_TWO_START,
                    durationSeconds = DURATION_SECONDS,
                    sourceType = Custom,
                    categoryId = CATEGORY_ID,
                    subcategoryIds = listOf(SUBCATEGORY_ID),
                    studiedCount = 1,
                    xpTotal = SERVER_XP_TOTAL,
                    readAloudEnabled = true,
                ),
                RecentSession.Rated(
                    id = SESSION_ONE_ID,
                    startedAt = SESSION_ONE_START,
                    durationSeconds = DURATION_SECONDS,
                    sourceType = SingleSubcategory,
                    categoryId = CATEGORY_ID,
                    subcategoryIds = listOf(SUBCATEGORY_ID),
                    studiedCount = 1,
                    xpTotal = SERVER_XP_TOTAL,
                    voiceAnsweringEnabled = false,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 0)
    }

    @Test
    fun `a server entry the mapper rejects is skipped and the rest still show`() = runTest {
        remoteRecents.value = RecentsStateDto(
            entries = listOf(remoteEntry(SESSION_ONE_ID, SESSION_ONE_START).copy(voiceAnswering = null), remoteEntry(SESSION_TWO_ID, SESSION_TWO_START)),
        )

        createRepository().observeRecentSessions().test {
            awaitItem().map(RecentSession::id) shouldBe listOf(SESSION_TWO_ID)
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 0)
    }

    @Test
    fun `a pending session shows with its preview XP and the session's own fields`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createRepository().observeRecentSessions().test {
            // 1 new card × 10 + 1 minute × 10 + 500 completion + a Streak of 1 × 20.
            awaitItem() shouldBe listOf(
                RecentSession.Fast(
                    id = SESSION_ONE_ID,
                    startedAt = SESSION_ONE_START,
                    durationSeconds = DURATION_SECONDS,
                    sourceType = SingleSubcategory,
                    categoryId = CATEGORY_ID,
                    subcategoryIds = listOf(SUBCATEGORY_ID),
                    studiedCount = 1,
                    xpTotal = 540,
                    readAloudEnabled = false,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `the server entry wins over the pending session with the same id`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        remoteRecents.value = RecentsStateDto(entries = listOf(remoteEntry(SESSION_ONE_ID, SESSION_ONE_START)))

        createRepository().observeRecentSessions().test {
            val recents = awaitItem()
            recents.map(RecentSession::id) shouldBe listOf(SESSION_ONE_ID)
            recents.single().xpTotal shouldBe SERVER_XP_TOTAL
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `a server entry arriving while its pending session is queued leaves one row with the server XP`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createRepository().observeRecentSessions().test {
            awaitItem().single().xpTotal shouldBe 540

            remoteRecents.value = RecentsStateDto(entries = listOf(remoteEntry(SESSION_ONE_ID, SESSION_ONE_START)))
            awaitItem().single().xpTotal shouldBe SERVER_XP_TOTAL

            pendingSessionQueue.remove(SESSION_ONE_ID)
            expectNoEvents()
        }

        verifyReads(projections = 2)
    }

    @Test
    fun `a pending session disappears once its queue entry is removed`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createRepository().observeRecentSessions().test {
            awaitItem().map(RecentSession::id) shouldBe listOf(SESSION_ONE_ID)

            pendingSessionQueue.remove(SESSION_ONE_ID)
            awaitItem() shouldBe emptyList()
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `another User's pending sessions are never included`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID), uid = OTHER_USER_ID)

        createRepository().observeRecentSessions().test {
            awaitItem() shouldBe emptyList()
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 0)
    }

    @Test
    fun `a pending session with no Flashcard Results is dropped`() = runTest {
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID))

        createRepository().observeRecentSessions().test {
            awaitItem().map(RecentSession::id) shouldBe listOf(SESSION_TWO_ID)
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `server and pending rows sort by start descending, ties by id descending`() = runTest {
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID))
        remoteRecents.value = RecentsStateDto(
            entries = listOf(remoteEntry(SESSION_ONE_ID, SESSION_ONE_START), remoteEntry(SESSION_THREE_ID, SESSION_ONE_START)),
        )

        createRepository().observeRecentSessions().test {
            awaitItem().map(RecentSession::id) shouldBe listOf(SESSION_TWO_ID, SESSION_THREE_ID, SESSION_ONE_ID)
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `the list is capped at 15 including pending, dropping the oldest row`() = runTest {
        remoteRecents.value = RecentsStateDto(entries = fullServerList())
        queue(fastSession(PENDING_SESSION_ID, SESSION_ONE_START.plusSeconds(MAX_RECENT_SESSIONS * HOUR_SECONDS), CARD_ID))

        createRepository().observeRecentSessions().test {
            val ids = awaitItem().map(RecentSession::id)
            ids.size shouldBe MAX_RECENT_SESSIONS
            ids.first() shouldBe PENDING_SESSION_ID
            ids.contains(serverSessionId(0)) shouldBe false
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `a pending session older than a full server list is not shown`() = runTest {
        remoteRecents.value = RecentsStateDto(entries = fullServerList())
        queue(fastSession(PENDING_SESSION_ID, SESSION_ONE_START.minusSeconds(HOUR_SECONDS), CARD_ID))

        createRepository().observeRecentSessions().test {
            val ids = awaitItem().map(RecentSession::id)
            ids.size shouldBe MAX_RECENT_SESSIONS
            ids.contains(PENDING_SESSION_ID) shouldBe false
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `a projection made stale by a queue change emits nothing for it`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            pendingSessionQueue.remove(SESSION_ONE_ID)
            null
        }
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createRepository().observeRecentSessions().test {
            awaitItem() shouldBe emptyList()
            cancelAndIgnoreRemainingEvents()
        }

        verifyReads(projections = 1)
    }

    @Test
    fun `a remote listener failure other than permission denied propagates`() = runTest {
        val error = IllegalStateException("listener failed")
        every { remoteDataSource.observeRecents() } returns flow { throw error }

        createRepository().observeRecentSessions().test {
            awaitError() shouldBe error
        }

        verifyReads(projections = 0)
    }

    private fun verifyReads(projections: Int) {
        verify(exactly = 1) { remoteDataSource.observeRecents() }
        coVerify(exactly = projections) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = projections) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    private fun fullServerList(): List<RecentSessionEntryDto> = (0 until MAX_RECENT_SESSIONS).map { index ->
        remoteEntry(serverSessionId(index), SESSION_ONE_START.plusSeconds(index * HOUR_SECONDS))
    }

    private fun serverSessionId(index: Int) = "server-session-$index"

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun remoteEntry(sessionId: String, startedAt: Instant) = RecentSessionEntryDto(
        sessionId = sessionId,
        startTimestamp = Timestamp(Date.from(startedAt)),
        durationSeconds = DURATION_SECONDS,
        studyMode = "Rated",
        voiceAnswering = false,
        readAloud = null,
        sourceType = SingleSubcategory.name,
        categoryId = CATEGORY_ID,
        subcategoryIds = listOf(SUBCATEGORY_ID),
        cardCount = 1,
        xpTotal = SERVER_XP_TOTAL,
    )

    private fun fastSession(id: String, startedAt: Instant, vararg cardIds: String) = SessionResult.Fast(
        id = id,
        startedAt = startedAt,
        durationSeconds = DURATION_SECONDS,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = 20,
        readAloudEnabled = false,
    )

    private fun authUser(uid: String) = AuthUser(uid = uid, email = null, displayName = null, photoUrl = null)

    private companion object {
        const val USER_ID = "user-1"
        const val OTHER_USER_ID = "user-2"
        const val CATEGORY_ID = "cat-1"
        const val SUBCATEGORY_ID = "sub-1"
        const val CARD_ID = "card-1"
        const val SESSION_ONE_ID = "session-1"
        const val SESSION_TWO_ID = "session-2"
        const val SESSION_THREE_ID = "session-3"
        const val PENDING_SESSION_ID = "pending-session"
        const val STUDY_DATE = "2026-09-06"
        const val DURATION_SECONDS = 60
        const val SERVER_XP_TOTAL = 999
        const val MAX_RECENT_SESSIONS = 15
        const val HOUR_SECONDS = 3_600L
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val SESSION_TWO_START: Instant = Instant.parse("2026-09-06T11:00:00Z")

        // The bundled defaults, except a small Streak rate, as in PendingSessionProjectorTest.
        val CONFIG = XpConfig(
            newCardStudied = 10,
            cardMastered = 100,
            masteryDefended = 50,
            minuteStudied = 10,
            sessionCompleted = 500,
            streakPerDay = 20,
            dailyGoalMet = 1000,
            levelCurveBase = 1000.0,
            levelCurveExponent = 2.5,
        )
    }
}
