package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveInternetAvailabilityUseCaseTest {

    private val networkAvailabilityGateway = mockk<NetworkAvailabilityGateway>()
    private val useCase = ObserveInternetAvailabilityUseCase(networkAvailabilityGateway)

    @Test
    fun `passes on the gateway's availability`() = runTest {
        every { networkAvailabilityGateway.observeInternetAvailability() } returns flowOf(false)

        useCase().first() shouldBe false
    }
}
