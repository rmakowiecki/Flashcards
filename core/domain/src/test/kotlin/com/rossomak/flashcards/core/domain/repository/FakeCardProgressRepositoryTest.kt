package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeCardProgressRepositoryTest {

    private fun summaryOf(masteredCount: Int): ProgressSummary =
        ProgressSummary(subcategories = mapOf("sub-1" to SubcategoryProgressSummary(masteredCount = masteredCount, studiedCount = masteredCount)))

    @Test
    fun `seedSummary calls made while a collector is parked on the read gate are not dropped`() = runTest {
        val repository = FakeCardProgressRepository()
        val gate = CompletableDeferred<Unit>()
        repository.summaryReadGate = gate

        val collected = mutableListOf<ProgressSummary?>()
        val job = launch { repository.observeProgressSummary().collect { collected.add(it) } }
        runCurrent()

        repository.seedSummary(summaryOf(masteredCount = 1))
        repository.seedSummary(summaryOf(masteredCount = 2))
        gate.complete(Unit)
        runCurrent()

        collected shouldBe listOf(summaryOf(masteredCount = 2))
        job.cancel()
    }

    @Test
    fun `seedSummary called after a collector has already subscribed is delivered as a live update`() = runTest {
        val repository = FakeCardProgressRepository()
        val collected = mutableListOf<ProgressSummary?>()
        val job = launch { repository.observeProgressSummary().collect { collected.add(it) } }
        runCurrent()

        repository.seedSummary(summaryOf(masteredCount = 3))
        runCurrent()

        collected shouldBe listOf(null, summaryOf(masteredCount = 3))
        job.cancel()
    }
}
