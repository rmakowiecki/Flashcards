package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.repository.LevelProgressRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveLevelProgressUseCaseTest {

    private val levelProgressRepository: LevelProgressRepository = mockk()

    @Test
    fun `emits what the repository emits`() = runTest {
        every { levelProgressRepository.observeLevelProgress() } returns flowOf(LEVEL_PROGRESS)

        ObserveLevelProgressUseCase(levelProgressRepository)().toList() shouldBe listOf(LEVEL_PROGRESS)
        verify(exactly = 1) { levelProgressRepository.observeLevelProgress() }
    }

    private companion object {
        val LEVEL_PROGRESS = LevelProgress(level = 2, xpIntoCurrentLevel = 400, xpForNextLevel = 6000)
    }
}
