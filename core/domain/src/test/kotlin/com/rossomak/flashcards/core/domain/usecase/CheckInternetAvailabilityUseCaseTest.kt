package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CheckInternetAvailabilityUseCaseTest {

    private val networkAvailabilityGateway = mockk<NetworkAvailabilityGateway>()
    private val useCase = CheckInternetAvailabilityUseCase(networkAvailabilityGateway)

    @Test
    fun `returns true when the gateway reports internet`() = runTest {
        every { networkAvailabilityGateway.isInternetAvailable() } returns true

        useCase() shouldBe true

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `returns false when the gateway reports no internet`() = runTest {
        every { networkAvailabilityGateway.isInternetAvailable() } returns false

        useCase() shouldBe false

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }
}
