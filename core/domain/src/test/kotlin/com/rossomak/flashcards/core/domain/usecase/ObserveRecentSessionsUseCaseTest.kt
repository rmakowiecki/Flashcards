package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.repository.FakeRecentSessionsRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CATEGORY_ID = "android"
private const val NEWER_CATEGORY_ID = "algorithms"
private const val SUBCATEGORY_ID = "compose"
private const val SESSION_ID = "session"
private const val NEWER_SESSION_ID = "newer-session"

class ObserveRecentSessionsUseCaseTest {

    private val recentSessionsRepository = FakeRecentSessionsRepository()
    private val flashcardRepository: FlashcardRepository = mockk()

    private val category = Category(
        id = CATEGORY_ID,
        name = "Android",
        order = 0,
        subcategoryCount = 3,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )
    private val quickSession = RecentSession.Fast(
        id = SESSION_ID,
        startedAt = Instant.EPOCH,
        durationSeconds = 300,
        sourceType = SessionSourceType.Quick,
        categoryId = CATEGORY_ID,
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        studiedCount = 10,
        xpTotal = 120,
        readAloudEnabled = false,
    )

    private fun createUseCase() = ObserveRecentSessionsUseCase(recentSessionsRepository, flashcardRepository)

    @Test
    fun `a Category fetch that fails once is retried and styles the Recent`() = runTest {
        recentSessionsRepository.setRecentSessions(listOf(quickSession))
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) } returnsMany listOf(
            Result.failure(IllegalStateException("categories fetch failed")),
            Result.success(listOf(category)),
        )

        val recentItems = createUseCase()().first()

        recentItems shouldBe listOf(RecentItem(quickSession, category))
        coVerify(exactly = 2) { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) }
        coVerify(exactly = 0) { flashcardRepository.fetchSubcategoriesByIds(any()) }
    }

    @Test
    fun `a Category fetch that fails on every attempt keeps the Recent without a Category after three attempts`() = runTest {
        recentSessionsRepository.setRecentSessions(listOf(quickSession))
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) } returns
            Result.failure(IllegalStateException("categories fetch failed"))

        val recentItems = createUseCase()().first()

        recentItems shouldBe listOf(RecentItem(quickSession, category = null))
        coVerify(exactly = 3) { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) }
        coVerify(exactly = 0) { flashcardRepository.fetchSubcategoriesByIds(any()) }
    }

    @Test
    fun `a newer list cancels the Category retries of an older one`() = runTest {
        val newerSession = quickSession.copy(id = NEWER_SESSION_ID, categoryId = NEWER_CATEGORY_ID)
        val newerCategory = category.copy(id = NEWER_CATEGORY_ID)
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) } returns
            Result.failure(IllegalStateException("categories fetch failed"))
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(NEWER_CATEGORY_ID)) } returns Result.success(listOf(newerCategory))
        recentSessionsRepository.setRecentSessions(listOf(quickSession))
        val emissions = mutableListOf<List<RecentItem>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { createUseCase()().toList(emissions) }
        runCurrent()

        recentSessionsRepository.setRecentSessions(listOf(newerSession))
        advanceUntilIdle()

        emissions shouldBe listOf(listOf(RecentItem(newerSession, newerCategory)))
    }
}
