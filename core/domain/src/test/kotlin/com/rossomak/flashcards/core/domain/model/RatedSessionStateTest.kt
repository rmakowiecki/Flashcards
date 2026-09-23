package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Correct
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating.PartiallyCorrect
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random
import org.junit.Test

class RatedSessionStateTest {

    private fun flashcard(id: String): Flashcard = Flashcard(
        id = id,
        subcategoryId = "sub-1",
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

    private fun cards(count: Int): List<Flashcard> = (1..count).map { flashcard("card-$it") }

    private fun state(
        cardCount: Int,
        attemptsLimit: Int = DEFAULT_ATTEMPTS_LIMIT,
        partialRatingCardRequeueingEnabled: Boolean = true,
        random: Random = Random(FIXED_SEED),
    ): RatedSessionState = RatedSessionState.seed(
        cards = cards(cardCount),
        attemptsLimit = attemptsLimit,
        partialRatingCardRequeueingEnabled = partialRatingCardRequeueingEnabled,
        random = random,
    )

    @Test
    fun `Correct on the first Attempt finishes the card as Mastered`() {
        val session = state(cardCount = 1)

        val outcome = rate(session, Correct)

        outcome.terminal shouldBe FlashcardTerminalRating.Mastered
        outcome.state.isComplete shouldBe true
    }

    @Test
    fun `Correct on a later Attempt still finishes the card as Mastered`() {
        val session = state(cardCount = 1, attemptsLimit = 3)

        val afterFailed = rate(session, Failed)
        afterFailed.terminal shouldBe null
        val afterCorrect = rate(afterFailed.state, Correct)

        afterCorrect.terminal shouldBe FlashcardTerminalRating.Mastered
    }

    @Test
    fun `Failed then Partial then Failed resolves to Terminal Partial, not Terminal Failed`() {
        val session = state(cardCount = 1, attemptsLimit = 3)

        val afterFailed = rate(session, Failed)
        afterFailed.terminal shouldBe null
        val afterPartial = rate(afterFailed.state, PartiallyCorrect)
        afterPartial.terminal shouldBe null
        val afterSecondFailed = rate(afterPartial.state, Failed)

        afterSecondFailed.terminal shouldBe FlashcardTerminalRating.Partial
    }

    @Test
    fun `exhausting Attempts having only ever rated Failed resolves to Terminal Failed`() {
        val session = state(cardCount = 1, attemptsLimit = 2)

        val afterFirstFailed = rate(session, Failed)
        afterFirstFailed.terminal shouldBe null
        val afterSecondFailed = rate(afterFirstFailed.state, Failed)

        afterSecondFailed.terminal shouldBe FlashcardTerminalRating.Failed
    }

    @Test
    fun `exhausting Attempts having been Partial at least once resolves to Terminal Partial`() {
        val session = state(cardCount = 1, attemptsLimit = 2)

        val afterPartial = rate(session, PartiallyCorrect)
        afterPartial.terminal shouldBe null
        val afterFailed = rate(afterPartial.state, Failed)

        afterFailed.terminal shouldBe FlashcardTerminalRating.Partial
    }

    @Test
    fun `a Partial rating is immediately Terminal Partial when partial requeueing is disabled`() {
        val session = state(cardCount = 1, attemptsLimit = 5, partialRatingCardRequeueingEnabled = false)

        val outcome = rate(session, PartiallyCorrect)

        outcome.terminal shouldBe FlashcardTerminalRating.Partial
        outcome.state.isComplete shouldBe true
    }

    @Test
    fun `a Partial rating re-inserts as normal when partial requeueing is enabled`() {
        val session = state(cardCount = 10, attemptsLimit = 5)

        val outcome = rate(session, PartiallyCorrect)

        outcome.terminal shouldBe null
        outcome.state.isComplete shouldBe false
        outcome.state.remainingCards.map { it.id } shouldContain FIRST_CARD_ID
    }

    @Test
    fun `a card at its Attempts limit is never re-inserted, even with other cards still queued`() {
        val session = state(cardCount = 3, attemptsLimit = 1)

        val outcome = rate(session, Failed)

        outcome.state.remainingCards.map { it.id } shouldNotContain FIRST_CARD_ID
    }

    @Test
    fun `an Attempts limit of 1 makes every rating immediately terminal`() {
        listOf(Failed, PartiallyCorrect, Correct).forEach { rating ->
            val session = state(cardCount = 1, attemptsLimit = 1)

            val outcome = rate(session, rating)

            outcome.terminal shouldNotBe null
            outcome.state.isComplete shouldBe true
        }
    }

    @Test
    fun `a Failed re-insertion always lands at index 2 to 4, never 0 or 1`() {
        repeat(REPETITIONS) {
            val session = state(cardCount = LARGE_POOL_SIZE, random = Random.Default)

            val outcome = rate(session, Failed)

            val index = outcome.state.remainingCards.indexOfFirst { it.id == FIRST_CARD_ID }
            (index in StudySessionConfig.FAILED_REQUEUE_MIN_GAP..StudySessionConfig.FAILED_REQUEUE_MAX_GAP) shouldBe true
        }
    }

    @Test
    fun `a Partial re-insertion always lands at index 5 to 9, never 0 or 1`() {
        repeat(REPETITIONS) {
            val session = state(cardCount = LARGE_POOL_SIZE, random = Random.Default)

            val outcome = rate(session, PartiallyCorrect)

            val index = outcome.state.remainingCards.indexOfFirst { it.id == FIRST_CARD_ID }
            (index in StudySessionConfig.PARTIAL_REQUEUE_MIN_GAP..StudySessionConfig.PARTIAL_REQUEUE_MAX_GAP) shouldBe true
        }
    }

    @Test
    fun `Failed re-insertion gap varies across repetitions rather than being fixed`() {
        val indices = (1..REPETITIONS).map {
            val session = state(cardCount = LARGE_POOL_SIZE, random = Random.Default)
            rate(session, Failed).state.remainingCards.indexOfFirst { it.id == FIRST_CARD_ID }
        }

        indices.distinct().size shouldNotBe 1
    }

    @Test
    fun `a re-insertion into a queue shorter than the drawn gap appends at the end`() {
        // Partial's minimum gap (5) exceeds the 2 cards left once the head is removed.
        val session = state(cardCount = 3, attemptsLimit = 5, random = Random.Default)

        val outcome = rate(session, PartiallyCorrect)

        outcome.state.remainingCards.last().id shouldBe FIRST_CARD_ID
    }

    @Test
    fun `a fixed Random produces the exact expected queue sequence for a known rating sequence`() {
        val ratingSequence = listOf(Failed, PartiallyCorrect, Failed, Correct, Failed)

        // Independently reproduces reinsertAt's exact draw order and gap ranges with an unrelated
        // Random instance seeded identically to the session's — asserting only that two
        // identically-seeded runs match each other (as this test previously did) would still pass
        // if reinsertAt stopped consulting state.random altogether (e.g. a hardcoded gap). Asserting
        // against this independently-computed expectation catches that regression too.
        val referenceRandom = Random(FIXED_SEED)
        val expectedQueue = cards(LARGE_POOL_SIZE).map { it.id }.toMutableList()
        val expectedHeadIds = ratingSequence.map { rating ->
            val head = expectedQueue.removeAt(0)
            if (rating != Correct) {
                val (minGap, maxGap) = when (rating) {
                    Failed -> StudySessionConfig.FAILED_REQUEUE_MIN_GAP to StudySessionConfig.FAILED_REQUEUE_MAX_GAP
                    PartiallyCorrect -> StudySessionConfig.PARTIAL_REQUEUE_MIN_GAP to StudySessionConfig.PARTIAL_REQUEUE_MAX_GAP
                    Correct -> error("unreachable — filtered out above")
                }
                val gap = referenceRandom.nextInt(minGap, maxGap + 1)
                expectedQueue.add(gap.coerceAtMost(expectedQueue.size), head)
            }
            head
        }

        var session = state(cardCount = LARGE_POOL_SIZE, attemptsLimit = 4, random = Random(FIXED_SEED))
        val actualHeadIds = ratingSequence.map { rating ->
            val cardId = session.currentCard?.id
            session = rate(session, rating).state
            cardId
        }

        actualHeadIds shouldBe expectedHeadIds
        session.remainingCards.map { it.id } shouldBe expectedQueue
    }

    @Test
    fun `distinct card count and mastered count do not move on a re-insertion`() {
        val session = state(cardCount = 5, attemptsLimit = 3)
        val distinctBefore = session.distinctCardCount
        val masteredBefore = session.masteredCount

        val outcome = rate(session, Failed)

        outcome.state.distinctCardCount shouldBe distinctBefore
        outcome.state.masteredCount shouldBe masteredBefore
    }

    @Test
    fun `mastered count increases only on a Terminal Mastered`() {
        val session = state(cardCount = 2, attemptsLimit = 1)

        val afterFailed = rate(session, Failed)
        afterFailed.state.masteredCount shouldBe 0

        val afterCorrect = rate(afterFailed.state, Correct)
        afterCorrect.state.masteredCount shouldBe 1
    }

    @Test
    fun `completed count increases on any Terminal State, regardless of grade`() {
        val session = state(cardCount = 2, attemptsLimit = 1)

        val afterFailed = rate(session, Failed)
        afterFailed.state.completedCount shouldBe 1
        afterFailed.state.masteredCount shouldBe 0

        val afterCorrect = rate(afterFailed.state, Correct)
        afterCorrect.state.completedCount shouldBe 2
    }

    @Test
    fun `currentCardRatings is empty for a card on its first Attempt`() {
        val session = state(cardCount = 1)

        session.currentCardRatings shouldBe emptyList()
    }

    @Test
    fun `currentCardRatings follows a card across a re-insertion, retaining its own Rating history`() {
        var session = state(cardCount = LARGE_POOL_SIZE, attemptsLimit = 3, random = Random.Default)

        session = rate(session, Failed).state
        // Fast-forward through whatever other cards sit ahead of card-1 until it is head again.
        while (session.currentCard?.id != FIRST_CARD_ID) session = rate(session, Correct).state

        session.currentCardRatings shouldBe listOf(Failed)
    }

    @Test
    fun `currentCardRatings is empty once the session is complete`() {
        val session = state(cardCount = 1, attemptsLimit = 1)

        val outcome = rate(session, Correct)

        outcome.state.currentCardRatings shouldBe emptyList()
    }

    @Test
    fun `requeueAfterSilence records no Rating and leaves the card's history unchanged`() {
        var session = state(cardCount = LARGE_POOL_SIZE, random = Random.Default)

        session = requeueAfterSilence(session)
        while (session.currentCard?.id != FIRST_CARD_ID) session = rate(session, Correct).state

        session.currentCardRatings shouldBe emptyList()
    }

    @Test
    fun `requeueAfterSilence does not move distinct card count or mastered count`() {
        val session = state(cardCount = 5, attemptsLimit = 3)
        val distinctBefore = session.distinctCardCount
        val masteredBefore = session.masteredCount

        val next = requeueAfterSilence(session)

        next.distinctCardCount shouldBe distinctBefore
        next.masteredCount shouldBe masteredBefore
    }

    @Test
    fun `requeueAfterSilence re-queues within the Failed gap range, never 0 or 1`() {
        repeat(REPETITIONS) {
            val session = state(cardCount = LARGE_POOL_SIZE, random = Random.Default)

            val next = requeueAfterSilence(session)

            val index = next.remainingCards.indexOfFirst { it.id == FIRST_CARD_ID }
            (index in StudySessionConfig.FAILED_REQUEUE_MIN_GAP..StudySessionConfig.FAILED_REQUEUE_MAX_GAP) shouldBe true
        }
    }

    @Test
    fun `the session reports complete exactly when every distinct card is terminal`() {
        var session = state(cardCount = 2, attemptsLimit = 1)

        session.isComplete shouldBe false
        session = rate(session, Correct).state
        session.isComplete shouldBe false
        session = rate(session, Correct).state
        session.isComplete shouldBe true
    }

    private companion object {
        const val DEFAULT_ATTEMPTS_LIMIT = 3
        const val FIXED_SEED = 42L
        const val REPETITIONS = 200
        const val LARGE_POOL_SIZE = 20
        const val FIRST_CARD_ID = "card-1"
    }
}
