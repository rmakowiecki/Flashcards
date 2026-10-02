package com.rossomak.flashcards.core.data.model

import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDomain
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class PendingSessionSubmissionMapperTest {

    private fun ratedSessionResult(sourceType: SessionSourceType = SingleSubcategory): SessionResult.Rated = SessionResult.Rated(
        id = "session-1",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = sourceType,
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
        studyDateUtcOffsetMinutes = -300,
        dailyGoalMinutes = 20,
    )

    private fun fastSessionResult(sourceType: SessionSourceType = SingleSubcategory): SessionResult.Fast = SessionResult.Fast(
        id = "session-2",
        startedAt = Instant.parse("2026-09-08T11:00:00Z"),
        durationSeconds = 30,
        abandoned = true,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = sourceType,
        cardResults = listOf(FlashcardResult.Fast(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen)),
        studyDate = "2026-09-08",
        studyDateUtcOffsetMinutes = -300,
        dailyGoalMinutes = 20,
    )

    @Test
    fun `toDto stamps the owning uid`() {
        val dto = fastSessionResult().toDto(UID)

        dto.uid shouldBe UID
    }

    @Test
    fun `toDomain throws for an entry without an owning uid`() {
        val malformedDto = fastSessionResult().toDto(UID).copy(uid = "")

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `toDto omits attemptsUsed and wasPreviouslyMastered for a Fast card result`() {
        val dto = fastSessionResult().toDto(UID)

        val cardResult = dto.cardResults.single()
        cardResult.attemptsUsed shouldBe null
        cardResult.wasPreviouslyMastered shouldBe null
    }

    @Test
    fun `toDomain throws for an unknown mode`() {
        val malformedDto = fastSessionResult().toDto(UID).copy(mode = "Unknown")

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `toDomain throws for an unknown card result state`() {
        val malformedDto = fastSessionResult().toDto(UID).let { dto ->
            dto.copy(cardResults = dto.cardResults.map { it.copy(state = "Unknown") })
        }

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `toDomain throws for a Rated entry missing attemptsUsed`() {
        val malformedDto = ratedSessionResult().toDto(UID).let { dto ->
            dto.copy(cardResults = dto.cardResults.map { it.copy(attemptsUsed = null) })
        }

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `toDomain throws for a Rated entry missing wasPreviouslyMastered`() {
        val malformedDto = ratedSessionResult().toDto(UID).let { dto ->
            dto.copy(cardResults = dto.cardResults.map { it.copy(wasPreviouslyMastered = null) })
        }

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `toDto then toDomain round-trips every source type of a Rated session`() {
        SessionSourceType.entries.forEach { sourceType ->
            val original = ratedSessionResult(sourceType = sourceType)

            val roundTripped = original.toDto(UID).toDomain()

            withClue(sourceType) { roundTripped shouldBe original }
        }
    }

    @Test
    fun `toDto then toDomain round-trips every source type of a Fast session`() {
        SessionSourceType.entries.forEach { sourceType ->
            val original = fastSessionResult(sourceType = sourceType)

            val roundTripped = original.toDto(UID).toDomain()

            withClue(sourceType) { roundTripped shouldBe original }
        }
    }

    @Test
    fun `toDto encodes the source type as its enum name`() {
        val sourceType = Quick

        val dto = ratedSessionResult(sourceType = sourceType).toDto(UID)

        dto.sourceType shouldBe sourceType.name
    }

    @Test
    fun `toDomain throws for an unknown source type`() {
        val malformedDto = fastSessionResult().toDto(UID).copy(sourceType = "Unknown")

        shouldThrow<IllegalArgumentException> { malformedDto.toDomain() }
    }

    @Test
    fun `a queue line without a source type fails to decode`() {
        val encodedDto = Json.encodeToJsonElement(ratedSessionResult().toDto(UID)).jsonObject
        val lineWithoutSourceType = JsonObject(encodedDto - "sourceType").toString()

        shouldThrow<SerializationException> { Json.decodeFromString<PendingSessionSubmissionDto>(lineWithoutSourceType) }
    }

    private companion object {
        const val UID = "uid-1"
    }
}
