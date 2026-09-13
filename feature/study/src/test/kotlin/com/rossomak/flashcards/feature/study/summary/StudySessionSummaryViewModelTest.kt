package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeSessionSubmissionRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.CalculateSessionXpUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * There is no past-session fallback path to test here — this route only ever carries a fresh
 * result, so every case below is the same single load path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudySessionSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val sessionSubmissionRepository = FakeSessionSubmissionRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val scoringStateRepository = FakeScoringStateRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()

    private fun createViewModel(): StudySessionSummaryViewModel = StudySessionSummaryViewModel(
        savedStateHandle,
        ObserveUserPreferencesUseCase(userPreferencesRepository),
        SubmitStudySessionUseCase(cardProgressRepository, scoringStateRepository, CalculateSessionXpUseCase(), sessionSubmissionRepository),
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun stubRoute(route: StudySessionSummaryRoute) {
        every { RouteDecoder.decode(any<() -> StudySessionSummaryRoute>()) } returns route
    }

    private fun ratedRoute(
        sessionId: String = "session-1",
        abandoned: Boolean = false,
        studyDateUtcOffsetMinutes: Int = -300,
        cardStates: List<FlashcardStudyProgressState> = listOf(
            FlashcardStudyProgressState.Mastered,
            FlashcardStudyProgressState.Mastered,
            FlashcardStudyProgressState.Partial,
            FlashcardStudyProgressState.Failed,
        ),
    ): StudySessionSummaryRoute {
        val cardIds = cardStates.indices.map { "card-$it" }
        return StudySessionSummaryRoute(
            sessionId = sessionId,
            mode = StudyMode.Rated,
            startedAtEpochSecond = 0L,
            studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
            durationSeconds = 120,
            abandoned = abandoned,
            categoryId = "cat-1",
            categoryName = "Category",
            subcategoryIds = listOf("sub-1"),
            subcategoryNames = listOf("Subcategory"),
            cardIds = cardIds,
            cardSubcategoryIds = cardIds.map { "sub-1" },
            cardStates = cardStates,
            cardAttemptsUsed = cardStates.map { 1 },
            cardWasPreviouslyMastered = cardStates.map { false },
        )
    }

    @Test
    fun `a Rated result exposes mode, duration, studied count and the three Terminal State counts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(ratedRoute())

            val viewModel = createViewModel()
            advanceUntilIdle()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Rated
                durationSeconds shouldBe 120
                studiedCount shouldBe 4
                abandoned shouldBe false
                masteredCount shouldBe 2
                partialCount shouldBe 1
                failedCount shouldBe 1
            }
        }

    @Test
    fun `an abandoned Rated result exposes the abandoned flag set`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute(abandoned = true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.abandoned shouldBe true
    }

    @Test
    fun `a Fast result exposes the reduced set with zero for every Terminal State count`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val cardIds = listOf("card-1", "card-2")
            stubRoute(
                StudySessionSummaryRoute(
                    sessionId = "session-2",
                    mode = StudyMode.Fast,
                    startedAtEpochSecond = 0L,
                    studyDateUtcOffsetMinutes = -300,
                    durationSeconds = 60,
                    abandoned = false,
                    categoryId = "cat-1",
                    categoryName = "Category",
                    subcategoryIds = listOf("sub-1"),
                    subcategoryNames = listOf("Subcategory"),
                    cardIds = cardIds,
                    cardSubcategoryIds = cardIds.map { "sub-1" },
                    cardStates = cardIds.map { FlashcardStudyProgressState.Seen },
                    // Rated-only (ADR-0014): null for a Fast route, not zero-filled lists.
                    cardAttemptsUsed = null,
                    cardWasPreviouslyMastered = null,
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Fast
                studiedCount shouldBe 2
                masteredCount shouldBe 0
                partialCount shouldBe 0
                failedCount shouldBe 0
            }
        }

    @Test
    fun `mode and Terminal State counts are already correct before the submission coroutine ever runs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test: mode/durationSeconds/studiedCount/abandoned/the three Terminal State
            // counts used to only get written inside submitSession()'s launched coroutine, after an
            // observeUserPreferences().first() suspend — leaving state.value at its
            // StudySessionSummaryScreenState default (StudyMode.Rated) until that coroutine ran. A
            // config change or slow first composition could observe that default, e.g. latching the
            // Ring/XpPour phase onto Rated and flashing the mastery ring on a genuine Fast session.
            // StandardTestDispatcher never runs a launched coroutine body until the dispatcher is
            // advanced, so reading state.value here — before any advanceUntilIdle() — exercises
            // exactly the window a real screen's first composition would see.
            stubRoute(ratedRoute())

            val viewModel = createViewModel()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Rated
                durationSeconds shouldBe 120
                studiedCount shouldBe 4
                abandoned shouldBe false
                masteredCount shouldBe 2
                partialCount shouldBe 1
                failedCount shouldBe 1
                // Still default: proves the assertions above were set independently of the
                // (not-yet-run) async dailyGoalMinutes/XP path, not a byproduct of the test racing
                // ahead of the whole coroutine.
                xpTotal shouldBe 0
            }
        }

    @Test
    fun `a Fast route's mode never reads as the Rated default before the submission coroutine ever runs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val cardIds = listOf("card-1", "card-2")
            stubRoute(
                StudySessionSummaryRoute(
                    sessionId = "session-2",
                    mode = StudyMode.Fast,
                    startedAtEpochSecond = 0L,
                    studyDateUtcOffsetMinutes = -300,
                    durationSeconds = 60,
                    abandoned = false,
                    categoryId = "cat-1",
                    categoryName = "Category",
                    subcategoryIds = listOf("sub-1"),
                    subcategoryNames = listOf("Subcategory"),
                    cardIds = cardIds,
                    cardSubcategoryIds = cardIds.map { "sub-1" },
                    cardStates = cardIds.map { FlashcardStudyProgressState.Seen },
                    cardAttemptsUsed = null,
                    cardWasPreviouslyMastered = null,
                ),
            )

            val viewModel = createViewModel()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Fast
                studiedCount shouldBe 2
                masteredCount shouldBe 0
                partialCount shouldBe 0
                failedCount shouldBe 0
            }
        }

    @Test
    fun `arriving at the summary submits the session exactly once`() = runTest(mainDispatcherRule.testDispatcher) {
        val route = ratedRoute()
        stubRoute(route)

        val viewModel = createViewModel()
        advanceUntilIdle()

        sessionSubmissionRepository.submittedSessionResults.size shouldBe 1
        sessionSubmissionRepository.submittedSessionResults.single().id shouldBe route.sessionId
        // Configuration change re-observes the same ViewModel instance rather than recreating it,
        // so a second read of state must not trigger a second submission.
        viewModel.state.value
        sessionSubmissionRepository.submittedSessionResults.size shouldBe 1
    }

    @Test
    fun `the submitted session carries studyDate derived from the route's own captured offset, not the device's current zone, and dailyGoalMinutes read fresh from preferences`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(dailyGoalMinutes = 45)
            // A route-carried offset deliberately different from ZoneId.systemDefault()'s own offset
            // for whatever machine runs this test — if submitSession() ever went back to re-deriving
            // the offset from the device's current zone instead of trusting the route, this would fail.
            val route = ratedRoute(studyDateUtcOffsetMinutes = -300)
            stubRoute(route)

            createViewModel()
            advanceUntilIdle()

            val submitted = sessionSubmissionRepository.submittedSessionResults.single()
            val expectedStudyDate = Instant.ofEpochSecond(route.startedAtEpochSecond)
                .plusSeconds(route.studyDateUtcOffsetMinutes * 60L)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toString()
            submitted.dailyGoalMinutes shouldBe 45
            submitted.studyDate shouldBe expectedStudyDate
            submitted.studyDateUtcOffsetMinutes shouldBe route.studyDateUtcOffsetMinutes
        }

    @Test
    fun `a failed session submission leaves the displayed preview intact and emits no message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(ratedRoute())
            sessionSubmissionRepository.resultToReturn = Result.failure(IllegalStateException("unauthenticated"))
            var messageReceived = false

            val viewModel = createViewModel()
            val collectJob = launch { viewModel.messages.collect { messageReceived = true } }
            advanceUntilIdle()

            // Submission to the server carries no further authority here and is never
            // reconciled against — a failed submission is not surfaced to the user at all (the
            // delivery queue is what makes delivery durable against exactly this kind of failure).
            messageReceived shouldBe false
            viewModel.state.value.studiedCount shouldBe 4
            viewModel.state.value.xpTotal shouldBe 785
            collectJob.cancel()
        }

    @Test
    fun `arriving at the summary computes and exposes the xp breakdown, total, level and progress`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(ratedRoute())

            val viewModel = createViewModel()
            advanceUntilIdle()

            // 4 new cards × 10 + 2 mastered × 100 + 1 partial × 25 + 2 minutes × 10 + 500 completion = 785.
            val config = XpConfig()
            with(viewModel.state.value) {
                xpTotal shouldBe 785
                level shouldBe 1
                xpIntoCurrentLevel shouldBe 785L
                xpForNextLevel shouldBe config.levelThreshold(1)
                xpLines.map { it.source } shouldNotContain XpAwardSource.MasteryDefended
                xpLines.map { it.source } shouldNotContain XpAwardSource.MasteryLost
            }
        }

    @Test
    fun `a failed scoring-state read leaves the xp fields at their defaults`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(IllegalStateException("firestore down"))
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            xpTotal shouldBe 0
            level shouldBe 1
            xpLines shouldBe emptyList()
        }
    }

    @Test
    fun `a failed scoring-state read still submits the session`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(IllegalStateException("firestore down"))
        stubRoute(ratedRoute())

        createViewModel()
        advanceUntilIdle()

        // Unlike the old client-write path, the preview's own local reads carry no gating power over
        // whether the session actually gets submitted — the function needs nothing from them.
        sessionSubmissionRepository.submittedSessionResults.size shouldBe 1
    }

    @Test
    fun `a failed scoring-state read surfaces a non-blocking message without clearing results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.resultToReturn = Result.failure(IllegalStateException("firestore down"))
            stubRoute(ratedRoute())
            var receivedMessage: StudySessionSummaryMessage? = null

            val viewModel = createViewModel()
            val collectJob = launch { viewModel.messages.collect { message -> receivedMessage = message } }
            advanceUntilIdle()

            receivedMessage shouldBe StudySessionSummaryMessage.SaveFailed
            viewModel.state.value.studiedCount shouldBe 4
            collectJob.cancel()
        }
}
