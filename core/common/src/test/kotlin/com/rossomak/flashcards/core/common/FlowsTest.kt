package com.rossomak.flashcards.core.common

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FlowsTest {

    @Test
    fun `flowFrom emits what the source flow emits`() = runTest {
        flowFrom { flowOf(1, 2) }.toList() shouldBe listOf(1, 2)
    }

    @Test
    fun `flowFrom hands a throw from the source call to a downstream catch`() = runTest {
        val fallback = -1

        val values = flowFrom<Int> { error("source failed") }
            .catch { emit(fallback) }
            .toList()

        values shouldBe listOf(fallback)
    }

    @Test
    fun `catchAndLog completes after the values that arrived before a failure`() = runTest {
        val values = flow {
            emit(1)
            error("stream failed")
        }
            .catchAndLog { "Observing the stream failed" }
            .toList()

        values shouldBe listOf(1)
    }
}
