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
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CATEGORY_ID = "android"
private const val SUBCATEGORY_ID = "compose"
private const val SESSION_ID = "session"

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
        subcategoryIds = listOf(SUBCATEGORY_ID),
        studiedCount = 10,
        xpTotal = 120,
        readAloudEnabled = false,
    )

    private fun createUseCase() = ObserveRecentSessionsUseCase(recentSessionsRepository, flashcardRepository)

    @Test
    fun `a Category fetch that fails once is retried and resolves the Recent`() = runTest {
        recentSessionsRepository.setRecentSessions(listOf(quickSession))
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) } returnsMany listOf(
            Result.failure(IllegalStateException("categories fetch failed")),
            Result.success(listOf(category)),
        )

        val recentItems = createUseCase()().first()

        recentItems shouldBe listOf(RecentItem(quickSession, category, emptyList()))
        coVerify(exactly = 2) { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) }
        coVerify(exactly = 0) { flashcardRepository.fetchSubcategoriesByIds(any()) }
    }

    @Test
    fun `a Category fetch that fails on every attempt drops the Recent after three attempts`() = runTest {
        recentSessionsRepository.setRecentSessions(listOf(quickSession))
        coEvery { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) } returns
            Result.failure(IllegalStateException("categories fetch failed"))

        val recentItems = createUseCase()().first()

        recentItems shouldBe emptyList()
        coVerify(exactly = 3) { flashcardRepository.fetchCategoriesByIds(setOf(CATEGORY_ID)) }
        coVerify(exactly = 0) { flashcardRepository.fetchSubcategoriesByIds(any()) }
    }
}
