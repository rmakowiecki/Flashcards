package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.model.XpConfigDto
import com.rossomak.flashcards.core.data.source.FakeXpConfigLocalDataSource
import com.rossomak.flashcards.core.data.source.XpConfigRemoteDataSource
import com.rossomak.flashcards.core.domain.model.XpConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultXpConfigRepositoryTest {

    private val remoteDataSource: XpConfigRemoteDataSource = mockk()
    private val localDataSource = FakeXpConfigLocalDataSource()
    private val repository = DefaultXpConfigRepository(remoteDataSource, localDataSource)

    @Test
    fun `getXpConfig serves the bundled defaults when nothing has been fetched yet`() = runTest {
        repository.getXpConfig().getOrThrow() shouldBe XpConfig()
    }

    @Test
    fun `getXpConfig serves the kept copy and never touches the network`() = runTest {
        localDataSource.savedConfig = SERVER_CONFIG

        repository.getXpConfig().getOrThrow() shouldBe SERVER_CONFIG
        coVerify(exactly = 0) { remoteDataSource.getXpConfig() }
    }

    @Test
    fun `a refreshed configuration is kept and then served`() = runTest {
        coEvery { remoteDataSource.getXpConfig() } returns SERVER_CONFIG.toDto()

        repository.refreshXpConfig()

        localDataSource.savedConfig shouldBe SERVER_CONFIG
        repository.getXpConfig().getOrThrow() shouldBe SERVER_CONFIG
    }

    @Test
    fun `a failed fetch keeps serving the kept copy and does not fail the caller`() = runTest {
        localDataSource.savedConfig = SERVER_CONFIG
        coEvery { remoteDataSource.getXpConfig() } throws IOException("offline")

        repository.refreshXpConfig()

        repository.getXpConfig().getOrThrow() shouldBe SERVER_CONFIG
    }

    @Test
    fun `a failed fetch with nothing kept serves the bundled defaults`() = runTest {
        coEvery { remoteDataSource.getXpConfig() } throws IOException("offline")

        repository.refreshXpConfig()

        localDataSource.savedConfig shouldBe null
        repository.getXpConfig().getOrThrow() shouldBe XpConfig()
    }

    @Test
    fun `a missing document leaves the kept copy unchanged`() = runTest {
        localDataSource.savedConfig = SERVER_CONFIG
        coEvery { remoteDataSource.getXpConfig() } returns null

        repository.refreshXpConfig()

        localDataSource.savedConfig shouldBe SERVER_CONFIG
    }

    @Test
    fun `an invalid document leaves the kept copy unchanged`() = runTest {
        localDataSource.savedConfig = SERVER_CONFIG
        coEvery { remoteDataSource.getXpConfig() } returns SERVER_CONFIG.toDto().copy(cardDemastered = POSITIVE_PENALTY)

        repository.refreshXpConfig()

        localDataSource.savedConfig shouldBe SERVER_CONFIG
    }

    @Test
    fun `refreshXpConfig rethrows cancellation instead of swallowing it`() = runTest {
        coEvery { remoteDataSource.getXpConfig() } throws CancellationException("cancelled")

        shouldThrow<CancellationException> { repository.refreshXpConfig() }
    }

    private fun XpConfig.toDto() = XpConfigDto(
        newCardStudied = newCardStudied.toLong(),
        cardMastered = cardMastered.toLong(),
        cardPartial = cardPartial.toLong(),
        masteryDefended = masteryDefended.toLong(),
        cardDemastered = cardDemastered.toLong(),
        sessionCompleted = sessionCompleted.toLong(),
        dailyGoalMet = dailyGoalMet.toLong(),
        streakPerDay = streakPerDay.toLong(),
        streakMaxPerDay = streakMaxPerDay.toLong(),
        minuteStudied = minuteStudied.toLong(),
        levelCurveBase = levelCurveBase,
        levelCurveExponent = levelCurveExponent,
    )

    private companion object {
        val SERVER_CONFIG = XpConfig(cardMastered = 200, minuteStudied = 5, levelCurveExponent = 2.0)
        const val POSITIVE_PENALTY = 80L
    }
}
