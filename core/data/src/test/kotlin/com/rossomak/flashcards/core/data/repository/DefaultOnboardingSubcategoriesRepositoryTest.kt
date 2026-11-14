package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.model.OnboardingSubcategoryDto
import com.rossomak.flashcards.core.data.source.OnboardingSubcategoriesRemoteDataSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOnboardingSubcategoriesRepositoryTest {

    private val remoteDataSource: OnboardingSubcategoriesRemoteDataSource = mockk()

    private fun createRepository(): DefaultOnboardingSubcategoriesRepository =
        DefaultOnboardingSubcategoriesRepository(remoteDataSource)

    private fun onboardingSubcategoryDto(subcategoryId: String, color: String? = null) = OnboardingSubcategoryDto(
        order = 0,
        categoryId = "android",
        categoryName = "Android",
        subcategoryId = subcategoryId,
        subcategoryName = "Compose",
        iconSvg = "<svg/>",
        color = color,
    )

    @Test
    fun `fetchOnboardingSubcategories maps dtos to domain models`() = runTest {
        coEvery { remoteDataSource.getOnboardingSubcategories() } returns
            listOf(onboardingSubcategoryDto("android-compose"))

        val result = createRepository().fetchOnboardingSubcategories()

        result.isSuccess shouldBe true
        result.getOrThrow().single().id shouldBe "android-compose"
    }

    @Test
    fun `fetchOnboardingSubcategories maps the dto color to the domain model`() = runTest {
        val categoryColor = "#3DDC84"
        coEvery { remoteDataSource.getOnboardingSubcategories() } returns
            listOf(onboardingSubcategoryDto("android-compose", color = categoryColor))

        val result = createRepository().fetchOnboardingSubcategories()

        result.getOrThrow().single().color shouldBe categoryColor
    }

    @Test
    fun `fetchOnboardingSubcategories maps a missing dto color to null`() = runTest {
        coEvery { remoteDataSource.getOnboardingSubcategories() } returns
            listOf(onboardingSubcategoryDto("android-compose", color = null))

        val result = createRepository().fetchOnboardingSubcategories()

        result.getOrThrow().single().color shouldBe null
    }

    @Test
    fun `fetchOnboardingSubcategories wraps data source failure in failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getOnboardingSubcategories() } throws error

        val result = createRepository().fetchOnboardingSubcategories()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
    }

    @Test
    fun `fetchOnboardingSubcategories rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.getOnboardingSubcategories() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().fetchOnboardingSubcategories() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
    }
}
