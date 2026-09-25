package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.source.VoiceOptionsDataSource
import com.rossomak.flashcards.core.domain.model.VoiceOption
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVoiceOptionsRepositoryTest {

    private val dataSource: VoiceOptionsDataSource = mockk()

    private fun createRepository(): DefaultVoiceOptionsRepository =
        DefaultVoiceOptionsRepository(dataSource)

    @Test
    fun `getAvailableVoices returns voices from data source unchanged`() = runTest {
        coEvery { dataSource.getAvailableVoices() } returns VOICES

        val result = createRepository().getAvailableVoices()

        result shouldBe VOICES
        coVerify(exactly = 1) { dataSource.getAvailableVoices() }
    }

    @Test
    fun `getAvailableVoices returns empty list when data source has no voices`() = runTest {
        coEvery { dataSource.getAvailableVoices() } returns emptyList()

        val result = createRepository().getAvailableVoices()

        result shouldBe emptyList()
        coVerify(exactly = 1) { dataSource.getAvailableVoices() }
    }

    @Test
    fun `repeated calls reach the data source once`() = runTest {
        coEvery { dataSource.getAvailableVoices() } returns VOICES
        val repository = createRepository()

        repository.getAvailableVoices()
        val secondResult = repository.getAvailableVoices()

        secondResult shouldBe VOICES
        coVerify(exactly = 1) { dataSource.getAvailableVoices() }
    }

    @Test
    fun `concurrent calls share one load`() = runTest {
        val pendingVoices = CompletableDeferred<List<VoiceOption>>()
        coEvery { dataSource.getAvailableVoices() } coAnswers { pendingVoices.await() }
        val repository = createRepository()

        val calls = List(CONCURRENT_CALLS) { async { repository.getAvailableVoices() } }
        runCurrent()
        pendingVoices.complete(VOICES)
        val results = calls.awaitAll()

        results shouldBe List(CONCURRENT_CALLS) { VOICES }
        coVerify(exactly = 1) { dataSource.getAvailableVoices() }
    }

    @Test
    fun `a failed load is not cached and a later call retries`() = runTest {
        coEvery { dataSource.getAvailableVoices() } throws IllegalStateException("engine failed") andThen VOICES
        val repository = createRepository()

        shouldThrow<IllegalStateException> { repository.getAvailableVoices() }
        val retryResult = repository.getAvailableVoices()

        retryResult shouldBe VOICES
        coVerify(exactly = 2) { dataSource.getAvailableVoices() }
    }

    private companion object {
        const val CONCURRENT_CALLS = 3
        val VOICES = listOf(
            VoiceOption(id = "en-us-x-1", countryCode = "US", variantIndex = 1),
            VoiceOption(id = "en-gb-x-2", countryCode = "GB", variantIndex = 1),
        )
    }
}
