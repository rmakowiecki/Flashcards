package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.XpConfigDto
import com.rossomak.flashcards.core.domain.model.XpConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class XpConfigMapperTest {

    @Test
    fun `a complete document maps to the configuration it holds`() {
        VALID_DTO.toDomain() shouldBe VALID_CONFIG
    }

    @Test
    fun `whole-number doubles are accepted for every numeric field`() {
        val dto = VALID_DTO.copy(cardMastered = 200.0, levelBaseCost = 1500.0, levelCostSteps = listOf(100.0, 300.0), levelsPerCostStep = 2.0)

        dto.toDomain() shouldBe VALID_CONFIG
    }

    @Test
    fun `a missing field is rejected, naming the field`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(cardPartial = null).toDomain() }
            .message shouldContain "cardPartial"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelBaseCost = null).toDomain() }
            .message shouldContain "levelBaseCost"
    }

    @Test
    fun `a non-finite field is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelBaseCost = Double.NaN).toDomain() }
            .message shouldContain "levelBaseCost"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(newCardStudied = Double.POSITIVE_INFINITY).toDomain() }
            .message shouldContain "newCardStudied"
    }

    @Test
    fun `a fractional award is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(sessionCompleted = 100.5).toDomain() }
            .message shouldContain "sessionCompleted must be an integer"
    }

    @Test
    fun `a fractional level curve value is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelBaseCost = 1500.5).toDomain() }
            .message shouldContain "levelBaseCost must be an integer"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelsPerCostStep = 2.5).toDomain() }
            .message shouldContain "levelsPerCostStep must be an integer"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = listOf(100L, 300.5)).toDomain() }
            .message shouldContain "levelCostSteps"
    }

    @Test
    fun `an award outside the Int range is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(dailyGoalMet = Long.MAX_VALUE).toDomain() }
            .message shouldContain "dailyGoalMet"
    }

    @Test
    fun `a positive de-mastery penalty is rejected and a zero one accepted`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(cardDemastered = 80L).toDomain() }
            .message shouldContain "cardDemastered"
        VALID_DTO.copy(cardDemastered = 0L).toDomain().cardDemastered shouldBe 0
    }

    @Test
    fun `a level base cost that is not positive is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelBaseCost = 0L).toDomain() }
            .message shouldContain "levelBaseCost"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelBaseCost = -1000L).toDomain() }
            .message shouldContain "levelBaseCost"
    }

    @Test
    fun `a levels per step count that is not positive is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelsPerCostStep = 0L).toDomain() }
            .message shouldContain "levelsPerCostStep"
    }

    @Test
    fun `a step list that is missing or empty is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = null).toDomain() }
            .message shouldContain "levelCostSteps"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = emptyList()).toDomain() }
            .message shouldContain "levelCostSteps"
    }

    @Test
    fun `a step that is not a positive number is rejected and a one-entry list accepted`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = listOf(100L, 0L)).toDomain() }
            .message shouldContain "levelCostSteps"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = listOf(100L, -300L)).toDomain() }
            .message shouldContain "levelCostSteps"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCostSteps = listOf(100L, null)).toDomain() }
            .message shouldContain "levelCostSteps"
        VALID_DTO.copy(levelCostSteps = listOf(100L)).toDomain().levelCostSteps shouldBe listOf(100)
    }

    private companion object {
        val VALID_DTO = XpConfigDto(
            newCardStudied = 10L,
            cardMastered = 200L,
            cardPartial = 25L,
            masteryDefended = 50L,
            cardDemastered = -100L,
            sessionCompleted = 100L,
            dailyGoalMet = 500L,
            streakPerDay = 250L,
            streakMaxPerDay = 1000L,
            levelBaseCost = 1500L,
            levelCostSteps = listOf(100L, 300L),
            levelsPerCostStep = 2L,
        )
        val VALID_CONFIG = XpConfig(cardMastered = 200, levelBaseCost = 1500, levelCostSteps = listOf(100, 300), levelsPerCostStep = 2)
    }
}
