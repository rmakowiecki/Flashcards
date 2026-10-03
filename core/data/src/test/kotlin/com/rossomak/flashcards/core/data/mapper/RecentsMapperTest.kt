package com.rossomak.flashcards.core.data.mapper

import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.model.RecentSessionEntryDto
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class RecentsMapperTest {

    @Test
    fun `a Rated entry maps with its voice answering flag and the start as an Instant`() {
        ratedEntry().toDomainOrNull() shouldBe RecentSession.Rated(
            id = SESSION_ID,
            startedAt = Instant.ofEpochSecond(START_SECONDS),
            durationSeconds = DURATION_SECONDS,
            sourceType = Quick,
            categoryId = CATEGORY_ID,
            subcategoryIds = listOf(SUBCATEGORY_ID),
            studiedCount = CARD_COUNT,
            xpTotal = XP_TOTAL,
            voiceAnsweringEnabled = true,
        )
    }

    @Test
    fun `a Fast entry maps with its read-aloud flag`() {
        val recent = ratedEntry().copy(studyMode = "Fast", voiceAnswering = null, readAloud = false).toDomainOrNull()

        (recent as RecentSession.Fast).readAloudEnabled shouldBe false
    }

    @Test
    fun `an unknown study mode maps to null`() {
        ratedEntry().copy(studyMode = "rated").toDomainOrNull() shouldBe null
    }

    @Test
    fun `an unknown source type maps to null`() {
        ratedEntry().copy(sourceType = "Composite").toDomainOrNull() shouldBe null
    }

    @Test
    fun `a Rated entry without voice answering maps to null, even with a read-aloud flag`() {
        ratedEntry().copy(voiceAnswering = null, readAloud = true).toDomainOrNull() shouldBe null
    }

    @Test
    fun `a Fast entry without read-aloud maps to null`() {
        ratedEntry().copy(studyMode = "Fast").toDomainOrNull() shouldBe null
    }

    private fun ratedEntry() = RecentSessionEntryDto(
        sessionId = SESSION_ID,
        startTimestamp = Timestamp(START_SECONDS, 0),
        durationSeconds = DURATION_SECONDS,
        studyMode = "Rated",
        voiceAnswering = true,
        readAloud = null,
        sourceType = Quick.name,
        categoryId = CATEGORY_ID,
        subcategoryIds = listOf(SUBCATEGORY_ID),
        cardCount = CARD_COUNT,
        xpTotal = XP_TOTAL,
    )

    private companion object {
        const val SESSION_ID = "session-1"
        const val CATEGORY_ID = "android"
        const val SUBCATEGORY_ID = "compose"
        const val START_SECONDS = 1_790_000_000L
        const val DURATION_SECONDS = 300
        const val CARD_COUNT = 12
        const val XP_TOTAL = 75
    }
}
