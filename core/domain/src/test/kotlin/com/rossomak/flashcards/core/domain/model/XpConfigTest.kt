package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.xpscoring.XpScoringCases
import io.kotest.matchers.shouldBe
import org.junit.Test

class XpConfigTest {

    @Test
    fun `the default constructor values match the shared default configuration file`() {
        XpConfig() shouldBe XpScoringCases.defaultConfig
    }
}
