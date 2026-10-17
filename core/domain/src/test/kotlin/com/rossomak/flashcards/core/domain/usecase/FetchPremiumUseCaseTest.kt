package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FetchPremiumUseCaseTest {

    private val premiumRepository = mockk<PremiumRepository>()
    private val useCase = FetchPremiumUseCase(premiumRepository)

    @Test
    fun `passes on the repository's fetch result`() = runTest {
        coEvery { premiumRepository.fetchPremium() } returns Result.success(false)

        useCase() shouldBe Result.success(false)

        coVerify(exactly = 1) { premiumRepository.fetchPremium() }
    }
}
