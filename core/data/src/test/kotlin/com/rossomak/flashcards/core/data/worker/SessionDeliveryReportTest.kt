package com.rossomak.flashcards.core.data.worker

import androidx.work.Data
import androidx.work.WorkInfo
import com.rossomak.flashcards.core.data.mapper.toDto
import com.rossomak.flashcards.core.data.model.DeliveredSessionDto
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.NotDelivered
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Rejected
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.junit.Test

class SessionDeliveryReportTest {

    @Test
    fun `a report reads back every session it was built from`() {
        val deliveredSessions = mapOf(
            "session-1" to DeliveredSessionDto.Scored(SCORE.toDto()),
            "session-2" to DeliveredSessionDto.Rejected,
        )

        SessionDeliveryReport.read(SessionDeliveryReport.toData(deliveredSessions)) shouldBe deliveredSessions
    }

    @Test
    fun `a report stops adding sessions before it would exceed WorkManager's data limit`() {
        val deliveredSessions = (1..200).associate { index -> "session-$index" to DeliveredSessionDto.Scored(SCORE.toDto()) }

        val data = SessionDeliveryReport.toData(deliveredSessions)
        val included = SessionDeliveryReport.read(data)

        included.size shouldBeGreaterThan 0
        included.size shouldBeLessThan deliveredSessions.size
        included shouldContainKey "session-1"
        data.toByteArray().size shouldBeLessThan Data.MAX_DATA_BYTES
    }

    @Test
    fun `data without a report reads as empty`() {
        SessionDeliveryReport.read(Data.EMPTY) shouldBe emptyMap()
    }

    @Test
    fun `a running drain reporting the session gives its status, one not reporting it gives none yet`() {
        val progress = SessionDeliveryReport.toData(mapOf(SESSION_ID to DeliveredSessionDto.Scored(SCORE.toDto())))

        sessionDeliveryStatusOf(workInfo(WorkInfo.State.RUNNING, progress = progress), SESSION_ID) shouldBe Scored(SCORE)
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.RUNNING), SESSION_ID) shouldBe null
    }

    @Test
    fun `a succeeded drain gives the reported status, or NotDelivered when the session is missing`() {
        val output = SessionDeliveryReport.toData(mapOf(SESSION_ID to DeliveredSessionDto.Rejected))

        sessionDeliveryStatusOf(workInfo(WorkInfo.State.SUCCEEDED, output = output), SESSION_ID) shouldBe Rejected
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.SUCCEEDED), SESSION_ID) shouldBe NotDelivered
    }

    @Test
    fun `an enqueued drain gives no status before its first attempt and NotDelivered after one`() {
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.ENQUEUED), SESSION_ID) shouldBe null
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.ENQUEUED, runAttemptCount = 1), SESSION_ID) shouldBe NotDelivered
    }

    @Test
    fun `a failed, cancelled or unknown drain gives NotDelivered and a blocked one gives no status yet`() {
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.FAILED), SESSION_ID) shouldBe NotDelivered
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.CANCELLED), SESSION_ID) shouldBe NotDelivered
        sessionDeliveryStatusOf(null, SESSION_ID) shouldBe NotDelivered
        sessionDeliveryStatusOf(workInfo(WorkInfo.State.BLOCKED), SESSION_ID) shouldBe null
    }

    private fun workInfo(
        state: WorkInfo.State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
        runAttemptCount: Int = 0,
    ): WorkInfo = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        outputData = output,
        progress = progress,
        runAttemptCount = runAttemptCount,
    )

    private companion object {
        const val SESSION_ID = "session-1"
        val SCORE = SessionScore(
            breakdown = XpBreakdown(newCards = 20, mastered = 100, partial = 25, timeStudied = 10, sessionCompletionBonus = 500, streakBonus = 250),
            level = 3,
            xpIntoCurrentLevel = 120,
            xpForNextLevel = 16000,
            levelsCrossed = listOf(2, 3),
            levelBefore = 1,
            xpIntoCurrentLevelBefore = 900,
            xpForNextLevelBefore = 1000,
            currentStreak = 8,
            counts = SessionScoreCounts(newCardsStudied = 2, newlyMastered = 1, partial = 1, defended = 0, demastered = 0),
            rates = SessionScoreRates(
                newCardStudied = 10,
                cardMastered = 100,
                cardPartial = 25,
                masteryDefended = 50,
                cardDemastered = -80,
                minuteStudied = 10,
                sessionCompleted = 500,
            ),
        )
    }
}
