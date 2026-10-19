package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetPremiumUseCaseTest {

    private val premiumRepository = mockk<PremiumRepository>()
    private val useCase = GetPremiumUseCase(premiumRepository)

    @Test
    fun `passes on the repository's fetch result`() = runTest {
        coEvery { premiumRepository.getPremium() } returns Result.success(false)

        useCase() shouldBe Result.success(false)

        coVerify(exactly = 1) { premiumRepository.getPremium() }
    }
}
