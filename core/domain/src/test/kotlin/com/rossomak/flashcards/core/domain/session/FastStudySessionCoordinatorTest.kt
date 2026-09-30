package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeAudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway.Call
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FastStudySessionCoordinatorTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val getSessionStartData = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressUseCase(cardProgressRepository),
    )
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val interruptionGateway = FakeAudioInterruptionGateway()

    private val setup = FastSessionSetup(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        cardIds = listOf("card-1", "card-2", "card-3"),
        sessionTitle = "Compose",
        voiceSettings = VoiceSettings(speechRate = SPEECH_RATE),
        readAloudEnabled = true,
    )

    private val events = mutableListOf<FastSessionEvent>()

    private fun flashcard(id: String): Flashcard = Flashcard(
        id = id,
        subcategoryId = SUBCATEGORY_ID,
        tags = emptyList(),
        question = "q-$id",
        answer = "a-$id",
        difficulty = 5,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null,
    )

    private val FastStudySessionCoordinator.runningSnapshot: FastSessionStateSnapshot.Running
        get() = sessionState.value.shouldBeInstanceOf<FastSessionStateSnapshot.Running>()

    private fun TestScope.startCoordinator(sessionSetup: FastSessionSetup = setup, runsLoad: Boolean = true): FastStudySessionCoordinator {
        flashcardRepository.flashcardsBySubcategory.putIfAbsent(SUBCATEGORY_ID, Result.success(sessionSetup.cardIds.map(::flashcard)))
        val coordinator = FastStudySessionCoordinator(
            getSessionStartData = getSessionStartData,
            playbackGateway = playbackGateway,
            interruptionGateway = interruptionGateway,
            reducer = FastSessionReducer(mockk(relaxed = true)),
            clock = Clock.fixed(START_INSTANT, ZoneOffset.UTC),
            timeSource = testScheduler.timeSource,
            logger = mockk(relaxed = true),
        )
        backgroundScope.launch { coordinator.events.collect { events += it } }
        coordinator.start(backgroundScope, sessionSetup)
        if (runsLoad) runCurrent()
        return coordinator
    }

    /** The player reads the presented question in full, and the pause after it runs out: the answer is presented. */
    private fun TestScope.reachAnswer() {
        playbackGateway.finishQuestion()
        runCurrent()
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE)
        runCurrent()
    }

    /** The player reads the presented answer in full, and the pause after it runs out. */
    private fun TestScope.finishCard() {
        playbackGateway.finishAnswer()
        runCurrent()
        advanceTimeBy(ANSWER_TO_NEXT_PAUSE)
        runCurrent()
    }

    /** Reads aloud from the first card to the question of card [index]. */
    private fun TestScope.moveToCard(index: Int) {
        repeat(index) {
            reachAnswer()
            finishCard()
        }
    }

    @Test
    fun `read-aloud starts the player with the loaded cards and the session's speech rate`() = runTest {
        startCoordinator()

        playbackGateway.startCalls.single() shouldBe Call.Start(listOf("card-1", "card-2", "card-3"), 0, "Compose", false)
        playbackGateway.lastSpeechRate shouldBe SPEECH_RATE
    }

    @Test
    fun `read-aloud off never starts the player`() = runTest {
        startCoordinator(setup.copy(readAloudEnabled = false))

        playbackGateway.startCalls.size shouldBe 0
    }

    // The rewind threshold

    @Test
    fun `previous within 3 seconds of a card starting goes to the previous card`() = runTest {
        startCoordinator().also { moveToCard(1) }
        advanceTimeBy(REWIND_THRESHOLD - 1.milliseconds)

        playbackGateway.emitExternal(TransportCommand.Previous)
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.PresentQuestion(0)
    }

    @Test
    fun `previous 3 seconds after a card started restarts it`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(1) }
        advanceTimeBy(REWIND_THRESHOLD)

        coordinator.previous()

        playbackGateway.calls.last() shouldBe Call.PresentQuestion(1)
    }

    @Test
    fun `previous on the first card always restarts it`() = runTest {
        val coordinator = startCoordinator()

        coordinator.previous()

        playbackGateway.calls.last() shouldBe Call.PresentQuestion(0)
    }

    // External commands

    @Test
    fun `external commands map onto the player`() = runTest {
        startCoordinator().also { moveToCard(1) }

        listOf(
            TransportCommand.Pause,
            TransportCommand.Play,
            TransportCommand.Next,
            TransportCommand.PreviousCard,
            TransportCommand.JumpTo(2),
            TransportCommand.Stop,
        ).forEach(playbackGateway::emitExternal)
        runCurrent()

        playbackGateway.calls.takeLast(6) shouldBe listOf(
            Call.Pause,
            Call.Play,
            Call.PresentAnswer(1),
            Call.PresentQuestion(0),
            Call.PresentQuestion(2),
            Call.Pause,
        )
        playbackGateway.stopCount shouldBe 0
    }

    @Test
    fun `an external command that changed the session is reported, and an ignored one is not`() = runTest {
        startCoordinator().also { moveToCard(2) }
        reachAnswer()

        playbackGateway.emitExternal(TransportCommand.Next)
        playbackGateway.emitExternal(TransportCommand.Play)
        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()

        events.filterIsInstance<FastSessionEvent.ExternalTransportCommand>() shouldBe
            listOf(FastSessionEvent.ExternalTransportCommand(TransportCommand.Pause))
    }

    // The advance hold

    @Test
    fun `with a hold requested read-aloud keeps playing and stops held at the auto-advance point`() = runTest {
        val coordinator = startCoordinator()

        coordinator.holdAdvance()
        reachAnswer()
        playbackGateway.pauseCount shouldBe 0
        finishCard()

        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe true
        coordinator.runningSnapshot.currentIndex shouldBe 0
        playbackGateway.calls.last() shouldBe Call.Pause
    }

    @Test
    fun `releasing a held session moves on and plays, and a second release changes nothing`() = runTest {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }

        coordinator.releaseAdvance()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()

        playbackGateway.calls.takeLast(2) shouldBe listOf(Call.PresentQuestion(1), Call.Play)
        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.currentIndex shouldBe 1
        val callCount = playbackGateway.calls.size

        coordinator.releaseAdvance()

        playbackGateway.calls.size shouldBe callCount
    }

    @Test
    fun `an external pause at a hold turns it into a user pause, and the release does not resume it`() = runTest {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }

        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()
        coordinator.releaseAdvance()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
        coordinator.runningSnapshot.currentIndex shouldBe 0
        playbackGateway.calls.last() shouldBe Call.Pause
    }

    @Test
    fun `external play at a hold moves on at once and is reported`() = runTest {
        externalCommandAtHoldMovesOn(TransportCommand.Play)
    }

    @Test
    fun `external next at a hold moves on at once and is reported`() = runTest {
        externalCommandAtHoldMovesOn(TransportCommand.Next)
    }

    private fun TestScope.externalCommandAtHoldMovesOn(command: TransportCommand) {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }

        playbackGateway.emitExternal(command)
        runCurrent()

        playbackGateway.calls.takeLast(2) shouldBe listOf(Call.PresentQuestion(1), Call.Play)
        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.currentIndex shouldBe 1
        events shouldContain FastSessionEvent.ExternalTransportCommand(command)
    }

    @Test
    fun `releasing a hold on the last card ends the session`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        holdAtAdvancePoint(coordinator)

        coordinator.releaseAdvance()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result
        result.abandoned shouldBe false
    }

    @Test
    fun `external play at a hold on the last card ends the session`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        holdAtAdvancePoint(coordinator)

        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result.abandoned shouldBe false
    }

    @Test
    fun `a temporary pause plays again when it ends, unless the user paused meanwhile`() = runTest {
        val coordinator = startCoordinator()

        coordinator.pauseTemporarily()
        runCurrent()
        playbackGateway.calls.last() shouldBe Call.Pause
        coordinator.endTemporaryPause()
        runCurrent()
        playbackGateway.calls.last() shouldBe Call.Play

        coordinator.pauseTemporarily()
        runCurrent()
        coordinator.pause()
        runCurrent()
        coordinator.endTemporaryPause()

        playbackGateway.calls.last() shouldBe Call.Pause
        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
    }

    @Test
    fun `an engine pause is never resumed by a release or the end of a temporary pause`() = runTest {
        val coordinator = startCoordinator()
        coordinator.holdAdvance()
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()

        coordinator.releaseAdvance()
        coordinator.endTemporaryPause()

        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.VoiceEngineUnavailable
        playbackGateway.startCalls.size shouldBe 1
    }

    @Test
    fun `a released hold keeps the held card for the linger, and a hold requested meanwhile keeps it held`() = runTest {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }

        coordinator.releaseAdvance()
        advanceTimeBy(RELEASE_LINGER - 1.milliseconds)
        runCurrent()
        coordinator.runningSnapshot.currentIndex shouldBe 0

        coordinator.holdAdvance()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()
        coordinator.runningSnapshot.currentIndex shouldBe 0
        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe true
    }

    @Test
    fun `a stopped session never moves on from a lingering release`() = runTest {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }
        coordinator.releaseAdvance()
        val presentedBefore = playbackGateway.presentedQuestions

        coordinator.stop()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()

        playbackGateway.presentedQuestions shouldBe presentedBefore
    }

    @Test
    fun `an input that arrives while another runs is applied after it, in order`() = runTest {
        val coordinator = startCoordinator().also { holdAtAdvancePoint(it) }
        playbackGateway.onCall = { call -> if (call is Call.PresentQuestion) coordinator.pause() }

        coordinator.releaseAdvance()
        advanceTimeBy(RELEASE_LINGER)
        runCurrent()

        playbackGateway.calls.takeLast(3) shouldBe listOf(Call.PresentQuestion(1), Call.Play, Call.Pause)
        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
    }

    @Test
    fun `transport commands reach the player only through the reducer's effects`() = runTest {
        val coordinator = startCoordinator()
        coordinator.pause()
        runCurrent()

        coordinator.pause()

        playbackGateway.pauseCount shouldBe 2
        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
    }

    private fun TestScope.holdAtAdvancePoint(coordinator: FastStudySessionCoordinator) {
        coordinator.holdAdvance()
        reachAnswer()
        finishCard()
        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe true
    }

    // Read-aloud next

    @Test
    fun `next at a question presents that card's answer, and next at an answer moves on`() = runTest {
        val coordinator = startCoordinator()

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.PresentAnswer(0)
        coordinator.runningSnapshot.currentIndex shouldBe 0
        coordinator.runningSnapshot.isAnswerRevealed shouldBe true

        coordinator.next()
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.PresentQuestion(1)
        coordinator.runningSnapshot.currentIndex shouldBe 1
    }

    @Test
    fun `next at the last card's answer does nothing, and the session ends only after that answer's pause`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        reachAnswer()
        val callCount = playbackGateway.calls.size

        (TransportCommandType.Next in coordinator.runningSnapshot.availableTransportCommands) shouldBe false
        coordinator.next()
        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        playbackGateway.calls.size shouldBe callCount
        events.filterIsInstance<FastSessionEvent.SessionEnded>().shouldBeEmpty()

        finishCard()

        events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result.abandoned shouldBe false
    }

    @Test
    fun `the system controls drop next at the last card's answer, and get it back on a restart`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        playbackGateway.availableCommandsUpdates.last() shouldBe TransportCommandType.entries.toSet()

        reachAnswer()
        playbackGateway.availableCommandsUpdates.last() shouldBe TransportCommandType.entries.toSet() - TransportCommandType.Next
        coordinator.runningSnapshot.availableTransportCommands shouldBe playbackGateway.availableCommandsUpdates.last()

        advanceTimeBy(REWIND_THRESHOLD)
        coordinator.previous()

        playbackGateway.availableCommandsUpdates.last() shouldBe TransportCommandType.entries.toSet()
    }

    @Test
    fun `read-aloud off never sends commands to the system controls`() = runTest {
        startCoordinator(setup.copy(readAloudEnabled = false))

        playbackGateway.availableCommandsUpdates shouldBe emptyList()
    }

    // The read-aloud pauses

    @Test
    fun `the answer is presented once the question pause has run in full, not before`() = runTest {
        val coordinator = startCoordinator()
        playbackGateway.finishQuestion()
        runCurrent()

        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE - 1.milliseconds)
        runCurrent()
        playbackGateway.presentedAnswers.shouldBeEmpty()

        advanceTimeBy(1.milliseconds)
        runCurrent()
        playbackGateway.presentedAnswers shouldBe listOf(0)
        coordinator.runningSnapshot.isAnswerRevealed shouldBe true
    }

    @Test
    fun `the next question is presented once the advance pause has run in full`() = runTest {
        val coordinator = startCoordinator()
        reachAnswer()
        playbackGateway.finishAnswer()
        runCurrent()

        advanceTimeBy(ANSWER_TO_NEXT_PAUSE - 1.milliseconds)
        runCurrent()
        coordinator.runningSnapshot.currentIndex shouldBe 0

        advanceTimeBy(1.milliseconds)
        runCurrent()
        coordinator.runningSnapshot.currentIndex shouldBe 1
        playbackGateway.calls.last() shouldBe Call.PresentQuestion(1)
    }

    @Test
    fun `a pause during the question pause cancels it, and play then presents the answer`() = runTest {
        val coordinator = startCoordinator()
        playbackGateway.finishQuestion()
        runCurrent()

        coordinator.pause()
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE * 2)
        runCurrent()
        playbackGateway.presentedAnswers.shouldBeEmpty()

        coordinator.play()
        runCurrent()
        playbackGateway.calls.takeLast(2) shouldBe listOf(Call.PresentAnswer(0), Call.Play)
    }

    @Test
    fun `ending the session cancels a running pause`() = runTest {
        val coordinator = startCoordinator()
        playbackGateway.finishQuestion()
        runCurrent()

        coordinator.end(abandoned = true)
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE * 2)
        runCurrent()

        playbackGateway.presentedAnswers.shouldBeEmpty()
    }

    // Engine unavailable

    @Test
    fun `an unavailable engine pauses the session, and play restarts the voice stack at the presented card`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.VoiceEngineUnavailable
        coordinator.runningSnapshot.currentIndex shouldBe 2
        events shouldContain FastSessionEvent.VoicePlaybackUnavailable
        playbackGateway.stopCount shouldBe 1

        coordinator.play()
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe null
        playbackGateway.startCalls.last().startIndex shouldBe 2
    }

    // Studied set and result

    @Test
    fun `the pause after the last card's answer ends the session with every Seen card`() = runTest {
        startCoordinator().also { moveToCard(2) }
        reachAnswer()
        playbackGateway.finishAnswer()
        runCurrent()

        advanceTimeBy(ANSWER_TO_NEXT_PAUSE - 1.milliseconds)
        runCurrent()
        events.filterIsInstance<FastSessionEvent.SessionEnded>().shouldBeEmpty()

        advanceTimeBy(1.milliseconds)
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result.shouldBeInstanceOf<SessionResult.Fast>()
        result.abandoned shouldBe false
        result.cardResults.map { it.cardId } shouldBe listOf("card-1", "card-2", "card-3")
    }

    @Test
    fun `ending the session stops the player and ignores later external commands`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(1) }

        coordinator.end(abandoned = true)
        val callsAfterEnd = playbackGateway.calls.size
        playbackGateway.emitExternal(TransportCommand.Next)
        playbackGateway.emitExternal(TransportCommand.JumpTo(0))
        runCurrent()

        playbackGateway.stopCount shouldBe 1
        playbackGateway.calls.size shouldBe callsAfterEnd
        events.filterIsInstance<FastSessionEvent.ExternalTransportCommand>().shouldBeEmpty()
    }

    @Test
    fun `an answer revealed just before a quick skip still counts as Seen`() = runTest {
        val coordinator = startCoordinator()
        // The skip lands before the player's answer report is handled.
        playbackGateway.emitExternal(TransportCommand.JumpTo(1))
        playbackGateway.emit(PlaybackEvent.AnswerRevealed("card-1"))
        runCurrent()

        coordinator.end(abandoned = true)
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result
        result.cardResults.map { it.cardId } shouldBe listOf("card-1")
    }

    @Test
    fun `leaving before the cards load seals an empty abandoned result with zero duration`() = runTest {
        val coordinator = startCoordinator(runsLoad = false)

        coordinator.end(abandoned = true)
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result
        result.abandoned shouldBe true
        result.cardResults shouldBe emptyList()
        result.durationSeconds shouldBe 0
    }

    @Test
    fun `the prior progress read is recorded, and a card with no entry is new`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgress(
                subcategoryId = SUBCATEGORY_ID,
                categoryId = "android",
                cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Seen, firstStudiedAt = START_INSTANT, masteredAt = null)),
            ),
        )

        val coordinator = startCoordinator(setup.copy(readAloudEnabled = false))

        coordinator.priorProgressByCardId.keys shouldBe setOf("card-1")
    }

    private companion object {
        const val SUBCATEGORY_ID = "android-compose"
        const val SPEECH_RATE = 1.5f
        val START_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
