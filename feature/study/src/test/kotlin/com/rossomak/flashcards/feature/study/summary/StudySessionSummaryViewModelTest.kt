package com.rossomak.flashcards.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.InFlight
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeSessionSubmissionRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDialog.XpBreakdown as XpBreakdownDialog
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.GreatWork
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.NiceEffort
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.PerfectRun
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored as ScoreResolved
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

    private val savedStateHandle = SavedStateHandle()
    private val sessionSubmissionRepository = FakeSessionSubmissionRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val scoringStateRepository = FakeScoringStateRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val xpConfigRepository = FakeXpConfigRepository()
    private val authRepository = FakeAuthRepository()

    private fun createViewModel(): StudySessionSummaryViewModel = StudySessionSummaryViewModel(
        savedStateHandle,
        ObserveUserPreferencesUseCase(userPreferencesRepository),
        SubmitStudySessionUseCase(cardProgressRepository, scoringStateRepository, GetXpConfigUseCase(xpConfigRepository), sessionSubmissionRepository),
        ObserveAuthUserUseCase(authRepository),
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun savedScore(): SessionScore? =
        savedStateHandle.get<String>(SAVED_SCORE_KEY)?.let { json -> Json.decodeFromString<SessionScore>(json) }

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
            sourceType = SingleSubcategory,
            cardIds = cardIds,
            cardSubcategoryIds = cardIds.map { "sub-1" },
            cardStates = cardStates,
            cardAttemptsUsed = cardStates.map { 1 },
            cardWasPreviouslyMastered = cardStates.map { false },
            voiceAnsweringEnabled = false,
            readAloudEnabled = null,
        )
    }

    private fun fastRoute(durationSeconds: Int = 120): StudySessionSummaryRoute {
        val cardIds = listOf("card-1", "card-2")
        return StudySessionSummaryRoute(
            sessionId = "session-2",
            mode = StudyMode.Fast,
            startedAtEpochSecond = 0L,
            studyDateUtcOffsetMinutes = -300,
            durationSeconds = durationSeconds,
            abandoned = false,
            categoryId = "cat-1",
            categoryName = "Category",
            subcategoryIds = listOf("sub-1"),
            subcategoryNames = listOf("Subcategory"),
            sourceType = SingleSubcategory,
            cardIds = cardIds,
            cardSubcategoryIds = cardIds.map { "sub-1" },
            cardStates = cardIds.map { FlashcardStudyProgressState.Seen },
            cardAttemptsUsed = null,
            cardWasPreviouslyMastered = null,
            voiceAnsweringEnabled = null,
            readAloudEnabled = false,
        )
    }

    @Test
    fun `a Rated result exposes mode, duration, studied count and the Mastered count`() =
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
    fun `a Fast result exposes a zero Mastered count`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(fastRoute(durationSeconds = 60))

            val viewModel = createViewModel()
            advanceUntilIdle()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Fast
                studiedCount shouldBe 2
                masteredCount shouldBe 0
            }
        }

    @Test
    fun `mode and Mastered count are already correct before the submission coroutine ever runs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test: mode/durationSeconds/studiedCount/abandoned/masteredCount used to only
            // get written inside submitSession()'s launched coroutine, after an
            // observeUserPreferences().first() suspend — leaving state.value at its
            // StudySessionSummaryScreenState default (StudyMode.Rated) until that coroutine ran. A
            // config change or slow first composition could observe that default, e.g. showing the
            // Rated pills on a genuine Fast session.
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
                // Still default: proves the assertions above were set independently of the
                // (not-yet-run) async dailyGoalMinutes/XP path, not a byproduct of the test racing
                // ahead of the whole coroutine.
                xpTotal shouldBe 0
                scoreStatus shouldBe Loading
            }
        }

    @Test
    fun `a Fast route's mode never reads as the Rated default before the submission coroutine ever runs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(fastRoute(durationSeconds = 60))

            val viewModel = createViewModel()

            with(viewModel.state.value) {
                mode shouldBe StudyMode.Fast
                studiedCount shouldBe 2
                masteredCount shouldBe 0
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
    fun `a server score renders the server's lines, counts, Level data before and after and Streak`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(SERVER_SCORE))

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            xpLines shouldBe listOf(
                XpBreakdownLine(XpAwardSource.NewCards, count = 3, rate = 20, amount = 60),
                XpBreakdownLine(XpAwardSource.Mastered, count = 1, rate = 200, amount = 200),
                XpBreakdownLine(XpAwardSource.MasteryDefended, count = 1, rate = 50, amount = 50),
                XpBreakdownLine(XpAwardSource.TimeStudied, count = 2, rate = 10, amount = 20),
                XpBreakdownLine(XpAwardSource.Streak, count = 8, rate = null, amount = 250),
                XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
                XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = 1000),
            )
            xpTotal shouldBe SERVER_SCORE.breakdown.xpTotal
            level shouldBe 3
            xpIntoCurrentLevel shouldBe 80L
            xpForNextLevel shouldBe 16000L
            levelsCrossed shouldBe listOf(2, 3)
            levelBefore shouldBe 1
            xpIntoCurrentLevelBefore shouldBe 900L
            xpForNextLevelBefore shouldBe 1000L
            currentStreak shouldBe 8
            scoreStatus shouldBe ScoreResolved
        }
    }

    @Test
    fun `a resolved server score is saved for a restore`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(SERVER_SCORE))

        createViewModel()
        advanceUntilIdle()

        savedScore() shouldBe SERVER_SCORE
    }

    @Test
    fun `a resolved local preview is saved for a restore`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        savedScore()?.breakdown?.xpTotal shouldBe viewModel.state.value.xpTotal
    }

    @Test
    fun `a failed preview is not saved, so a restore submits again`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())

        createViewModel()
        advanceUntilIdle()

        savedStateHandle.contains(SAVED_SCORE_KEY) shouldBe false
    }

    @Test
    fun `a restored ViewModel with a saved score shows it and never submits`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        savedStateHandle[SAVED_SCORE_KEY] = Json.encodeToString(SERVER_SCORE)

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            xpTotal shouldBe SERVER_SCORE.breakdown.xpTotal
            level shouldBe 3
            levelBefore shouldBe 1
            currentStreak shouldBe 8
            xpLines.size shouldBe SERVER_LINE_COUNT
            scoreStatus shouldBe ScoreResolved
        }
        sessionSubmissionRepository.submittedSessionResults shouldBe emptyList()
        scoringStateRepository.getScoringStateCallCount shouldBe 0
    }

    @Test
    fun `a server score needs none of the local preview's reads`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(SERVER_SCORE))
        var messageReceived = false

        val viewModel = createViewModel()
        val collectJob = launch { viewModel.messages.collect { messageReceived = true } }
        advanceUntilIdle()

        messageReceived shouldBe false
        viewModel.state.value.xpTotal shouldBe SERVER_SCORE.breakdown.xpTotal
        collectJob.cancel()
    }

    @Test
    fun `the score stays Loading until the submission returns`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flow {
            emit(InFlight)
            delay(3.seconds)
            emit(Scored(SERVER_SCORE))
        }

        val viewModel = createViewModel()
        advanceTimeBy(2.seconds)

        viewModel.state.value.scoreStatus shouldBe Loading
        viewModel.state.value.xpLines shouldBe emptyList()

        advanceUntilIdle()

        viewModel.state.value.scoreStatus shouldBe ScoreResolved
        viewModel.state.value.xpTotal shouldBe SERVER_SCORE.breakdown.xpTotal
    }

    @Test
    fun `with no server score the summary computes and exposes the local preview's breakdown, total, Level before and after and Streak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(ratedRoute())

            val viewModel = createViewModel()
            advanceUntilIdle()

            // 4 new cards × 10 + 2 mastered × 100 + 1 partial × 25 + 2 minutes × 10 + 500 completion + the
            // first day of a Streak × 250 = 1035, which crosses Level 1's threshold of 1000.
            val config = XpConfig()
            with(viewModel.state.value) {
                xpLines shouldBe listOf(
                    XpBreakdownLine(XpAwardSource.NewCards, count = 4, rate = 10, amount = 40),
                    XpBreakdownLine(XpAwardSource.Mastered, count = 2, rate = 100, amount = 200),
                    XpBreakdownLine(XpAwardSource.Partial, count = 1, rate = 25, amount = 25),
                    XpBreakdownLine(XpAwardSource.TimeStudied, count = 2, rate = 10, amount = 20),
                    XpBreakdownLine(XpAwardSource.Streak, count = 1, rate = null, amount = 250),
                    XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
                )
                xpTotal shouldBe 1035
                level shouldBe 2
                xpIntoCurrentLevel shouldBe 35L
                xpForNextLevel shouldBe config.levelThreshold(2)
                levelBefore shouldBe ScoringState.STARTING_LEVEL
                xpIntoCurrentLevelBefore shouldBe 0L
                xpForNextLevelBefore shouldBe config.levelThreshold(ScoringState.STARTING_LEVEL)
                currentStreak shouldBe 1
                scoreStatus shouldBe ScoreResolved
            }
        }

    @Test
    fun `the local preview's line counts come from the score, so a card Mastered before the session shows as defended`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // The route says card-0 was not previously Mastered; the prior Card Progress says it was.
            cardProgressRepository.seed(
                SubcategoryProgressDetails(
                    subcategoryId = "sub-1",
                    categoryId = "cat-1",
                    cards = mapOf("card-0" to CardProgressEntry(FlashcardStudyProgressState.Mastered, firstStudiedAt = Instant.EPOCH, masteredAt = Instant.EPOCH)),
                ),
            )
            stubRoute(ratedRoute())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.xpLines shouldBe listOf(
                XpBreakdownLine(XpAwardSource.NewCards, count = 3, rate = 10, amount = 30),
                XpBreakdownLine(XpAwardSource.Mastered, count = 1, rate = 100, amount = 100),
                XpBreakdownLine(XpAwardSource.Partial, count = 1, rate = 25, amount = 25),
                XpBreakdownLine(XpAwardSource.MasteryDefended, count = 1, rate = 50, amount = 50),
                XpBreakdownLine(XpAwardSource.TimeStudied, count = 2, rate = 10, amount = 20),
                XpBreakdownLine(XpAwardSource.Streak, count = 1, rate = null, amount = 250),
                XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
            )
        }

    @Test
    fun `the local preview is scored with the cached xp configuration`() = runTest(mainDispatcherRule.testDispatcher) {
        xpConfigRepository.resultToReturn = Result.success(XpConfig(cardMastered = 321))
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.xpLines.single { it.source == XpAwardSource.Mastered } shouldBe
            XpBreakdownLine(XpAwardSource.Mastered, count = 2, rate = 321, amount = 642)
    }

    @Test
    fun `a failed scoring-state read leaves the xp fields at their defaults`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            xpTotal shouldBe 0
            level shouldBe 1
            xpLines shouldBe emptyList()
            scoreStatus shouldBe Unavailable
        }
    }

    @Test
    fun `a failed scoring-state read still submits the session`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())

        createViewModel()
        advanceUntilIdle()

        // Unlike the old client-write path, the preview's own local reads carry no gating power over
        // whether the session actually gets submitted — the function needs nothing from them.
        sessionSubmissionRepository.submittedSessionResults.size shouldBe 1
    }

    @Test
    fun `a failed scoring-state read surfaces the XP unavailable message without clearing results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
            stubRoute(ratedRoute())
            var receivedMessage: StudySessionSummaryMessage? = null

            val viewModel = createViewModel()
            val collectJob = launch { viewModel.messages.collect { message -> receivedMessage = message } }
            advanceUntilIdle()

            receivedMessage shouldBe StudySessionSummaryMessage.XpUnavailable
            viewModel.state.value.studiedCount shouldBe 4
            collectJob.cancel()
        }

    @Test
    fun `the signed-in User's photo and name reach state`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = AUTH_USER
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state.value) {
            photoUrl shouldBe AUTH_USER_PHOTO_URL
            displayName shouldBe AUTH_USER_DISPLAY_NAME
        }
    }

    @Test
    fun `a null auth emission clears the photo and name`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = AUTH_USER
        stubRoute(ratedRoute())
        val viewModel = createViewModel()
        advanceUntilIdle()

        authRepository.userToReturn = null
        advanceUntilIdle()

        with(viewModel.state.value) {
            photoUrl shouldBe null
            displayName shouldBe null
        }
    }

    @Test
    fun `every source is listed in the dialog order, multiplied lines with count and rate`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(EVERY_SOURCE_SCORE))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.xpLines shouldBe listOf(
            XpBreakdownLine(XpAwardSource.NewCards, count = 4, rate = 20, amount = 80),
            XpBreakdownLine(XpAwardSource.Mastered, count = 2, rate = 200, amount = 400),
            XpBreakdownLine(XpAwardSource.Partial, count = 1, rate = 25, amount = 25),
            XpBreakdownLine(XpAwardSource.MasteryDefended, count = 1, rate = 50, amount = 50),
            XpBreakdownLine(XpAwardSource.MasteryLost, count = 1, rate = -80, amount = -80),
            XpBreakdownLine(XpAwardSource.TimeStudied, count = 2, rate = 10, amount = 20),
            XpBreakdownLine(XpAwardSource.Streak, count = 8, rate = null, amount = 250),
            XpBreakdownLine(XpAwardSource.SessionCompleted, count = null, rate = null, amount = 500),
            XpBreakdownLine(XpAwardSource.DailyGoal, count = null, rate = null, amount = 1000),
        )
    }

    @Test
    fun `a source with a zero amount is dropped from the lines`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(SERVER_SCORE))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.xpLines.map { it.source } shouldBe listOf(
            XpAwardSource.NewCards,
            XpAwardSource.Mastered,
            XpAwardSource.MasteryDefended,
            XpAwardSource.TimeStudied,
            XpAwardSource.Streak,
            XpAwardSource.SessionCompleted,
            XpAwardSource.DailyGoal,
        )
    }

    @Test
    fun `the Streak line carries the current Streak and the completion and daily goal lines carry neither count nor rate`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(ratedRoute())
            sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(EVERY_SOURCE_SCORE))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val linesBySource = viewModel.state.value.xpLines.associateBy { it.source }
            linesBySource.getValue(XpAwardSource.Streak).count shouldBe EVERY_SOURCE_SCORE.currentStreak
            linesBySource.getValue(XpAwardSource.Streak).rate shouldBe null
            listOf(XpAwardSource.SessionCompleted, XpAwardSource.DailyGoal).forEach { source ->
                linesBySource.getValue(source).count shouldBe null
                linesBySource.getValue(source).rate shouldBe null
            }
        }

    @Test
    fun `a Fast session never lists a Rated-only line`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(fastRoute())
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(EVERY_SOURCE_SCORE))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.xpLines.map { it.source } shouldBe listOf(
            XpAwardSource.NewCards,
            XpAwardSource.TimeStudied,
            XpAwardSource.Streak,
            XpAwardSource.SessionCompleted,
            XpAwardSource.DailyGoal,
        )
    }

    @Test
    fun `a Fast session under a minute has no Time Studied line`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(fastRoute(durationSeconds = 59))
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(UNDER_A_MINUTE_SCORE))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.xpLines.map { it.source } shouldBe listOf(
            XpAwardSource.NewCards,
            XpAwardSource.Streak,
            XpAwardSource.SessionCompleted,
        )
    }

    @Test
    fun `a session the cached state already applied is Unavailable with no numbers and the XP unavailable message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val route = ratedRoute()
            scoringStateRepository.appliedSessionIds = setOf(route.sessionId)
            stubRoute(route)
            var receivedMessage: StudySessionSummaryMessage? = null

            val viewModel = createViewModel()
            val collectJob = launch { viewModel.messages.collect { message -> receivedMessage = message } }
            advanceUntilIdle()

            with(viewModel.state.value) {
                scoreStatus shouldBe Unavailable
                xpLines shouldBe emptyList()
                xpTotal shouldBe 0
            }
            receivedMessage shouldBe StudySessionSummaryMessage.XpUnavailable
            collectJob.cancel()
        }

    @Test
    fun `a Rated session with every card Mastered is a perfect run`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute(cardStates = List(3) { FlashcardStudyProgressState.Mastered }))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.headline shouldBe PerfectRun
    }

    @Test
    fun `a Rated session with a card not Mastered is great work`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.headline shouldBe GreatWork
    }

    @Test
    fun `a finished Fast session is great work, never a perfect run`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(fastRoute())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.headline shouldBe GreatWork
    }

    @Test
    fun `an abandoned session is nice effort even with every card Mastered`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute(abandoned = true, cardStates = List(3) { FlashcardStudyProgressState.Mastered }))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.headline shouldBe NiceEffort
    }

    @Test
    fun `a negative total turns the headline to nice effort once the score lands`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute(cardStates = List(3) { FlashcardStudyProgressState.Mastered }))
        sessionSubmissionRepository.deliveryStatusToReturn = flow {
            emit(InFlight)
            delay(3.seconds)
            emit(Scored(LOSS_SCORE))
        }

        val viewModel = createViewModel()
        advanceTimeBy(2.seconds)

        viewModel.state.value.headline shouldBe PerfectRun

        advanceUntilIdle()

        viewModel.state.value.headline shouldBe NiceEffort
        viewModel.state.value.xpTotal shouldBe LOSS_SCORE.breakdown.xpTotal
    }

    @Test
    fun `a restored score is Scored from the first frame`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        savedStateHandle[SAVED_SCORE_KEY] = Json.encodeToString(SERVER_SCORE)

        val viewModel = createViewModel()

        viewModel.state.value.scoreStatus shouldBe ScoreResolved
    }

    @Test
    fun `a score that cannot be computed turns Unavailable after Loading`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())

        val viewModel = createViewModel()

        viewModel.state.value.scoreStatus shouldBe Loading

        advanceUntilIdle()

        viewModel.state.value.scoreStatus shouldBe Unavailable
    }

    @Test
    fun `opening the XP breakdown shows it until Confirm closes it`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(XpBreakdownDialog))
        viewModel.state.value.activeDialog shouldBe XpBreakdownDialog

        viewModel.onDialogEvent(Confirm)
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `opening the XP breakdown shows it until Dismiss closes it`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(XpBreakdownDialog))
        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `opening the XP breakdown while the score is Loading is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(ratedRoute())
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(XpBreakdownDialog))

        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `opening the XP breakdown while the score is Unavailable is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.resultToReturn = Result.failure(FIRESTORE_DOWN)
        stubRoute(ratedRoute())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(XpBreakdownDialog))

        viewModel.state.value.activeDialog shouldBe null
    }

    private companion object {
        const val SAVED_SCORE_KEY = "savedScore"
        const val SERVER_LINE_COUNT = 7
        val FIRESTORE_DOWN = IllegalStateException("firestore down")
        const val AUTH_USER_PHOTO_URL = "https://example.com/jane.jpg"
        const val AUTH_USER_DISPLAY_NAME = "Jane Doe"
        val AUTH_USER = AuthUser(
            uid = "uid-1",
            email = "jane@example.com",
            displayName = AUTH_USER_DISPLAY_NAME,
            photoUrl = AUTH_USER_PHOTO_URL,
        )
        val SERVER_SCORE = SessionScore(
            breakdown = XpBreakdown(
                newCards = 60,
                mastered = 200,
                masteryDefenseBonus = 50,
                timeStudied = 20,
                sessionCompletionBonus = 500,
                dailyGoalBonus = 1000,
                streakBonus = 250,
            ),
            level = 3,
            xpIntoCurrentLevel = 80,
            xpForNextLevel = 16000,
            levelsCrossed = listOf(2, 3),
            levelBefore = 1,
            xpIntoCurrentLevelBefore = 900,
            xpForNextLevelBefore = 1000,
            currentStreak = 8,
            counts = SessionScoreCounts(newCardsStudied = 3, newlyMastered = 1, partial = 0, defended = 1, demastered = 0),
            rates = SessionScoreRates(
                newCardStudied = 20,
                cardMastered = 200,
                cardPartial = 25,
                masteryDefended = 50,
                cardDemastered = -80,
                minuteStudied = 10,
                sessionCompleted = 500,
            ),
        )
        val EVERY_SOURCE_SCORE = SERVER_SCORE.copy(
            breakdown = XpBreakdown(
                newCards = 80,
                mastered = 400,
                partial = 25,
                masteryDefenseBonus = 50,
                demastered = -80,
                timeStudied = 20,
                sessionCompletionBonus = 500,
                dailyGoalBonus = 1000,
                streakBonus = 250,
            ),
            counts = SessionScoreCounts(newCardsStudied = 4, newlyMastered = 2, partial = 1, defended = 1, demastered = 1),
        )
        val UNDER_A_MINUTE_SCORE = SERVER_SCORE.copy(
            breakdown = XpBreakdown(newCards = 40, sessionCompletionBonus = 500, streakBonus = 250),
            counts = SessionScoreCounts(newCardsStudied = 2, newlyMastered = null, partial = null, defended = null, demastered = null),
        )
        val LOSS_SCORE = SERVER_SCORE.copy(
            breakdown = XpBreakdown(demastered = -240),
            counts = SessionScoreCounts(newCardsStudied = 0, newlyMastered = 0, partial = 0, defended = 0, demastered = 3),
        )
    }
}
