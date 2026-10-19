package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePremiumUseCaseTest {

    private val premiumRepository = mockk<PremiumRepository>()
    private val useCase = ObservePremiumUseCase(premiumRepository)

    @Test
    fun `passes on the repository's Premium value`() = runTest {
        every { premiumRepository.observePremium() } returns flowOf(null)

        useCase().first() shouldBe null
    }
}
