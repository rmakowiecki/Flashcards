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
        VALID_DTO.toDomain() shouldBe XpConfig(cardMastered = 200, levelCurveExponent = 2.0)
    }

    @Test
    fun `whole-number doubles are accepted as awards and whole-number longs as curve values`() {
        val dto = VALID_DTO.copy(cardMastered = 200.0, levelCurveBase = 1000L)

        dto.toDomain() shouldBe XpConfig(cardMastered = 200, levelCurveExponent = 2.0)
    }

    @Test
    fun `a missing field is rejected, naming the field`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(minuteStudied = null).toDomain() }
            .message shouldContain "minuteStudied"
    }

    @Test
    fun `a non-finite field is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCurveExponent = Double.NaN).toDomain() }
            .message shouldContain "levelCurveExponent"
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(newCardStudied = Double.POSITIVE_INFINITY).toDomain() }
            .message shouldContain "newCardStudied"
    }

    @Test
    fun `a fractional award is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(sessionCompleted = 500.5).toDomain() }
            .message shouldContain "sessionCompleted must be an integer"
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
    fun `a level curve base that is not positive is rejected`() {
        shouldThrow<IllegalArgumentException> { VALID_DTO.copy(levelCurveBase = 0.0).toDomain() }
            .message shouldContain "levelCurveBase"
    }

    private companion object {
        val VALID_DTO = XpConfigDto(
            newCardStudied = 10L,
            cardMastered = 200L,
            cardPartial = 25L,
            masteryDefended = 50L,
            cardDemastered = -80L,
            sessionCompleted = 500L,
            dailyGoalMet = 1000L,
            streakPerDay = 250L,
            streakMaxPerDay = 2500L,
            minuteStudied = 10L,
            levelCurveBase = 1000.0,
            levelCurveExponent = 2.0,
        )
    }
}
