package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.UserFavorites
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUserFavoritesRepositoryTest {

    private val remoteDataSource: UserFavoritesRemoteDataSource = mockk()

    private fun createRepository(): DefaultUserFavoritesRepository =
        DefaultUserFavoritesRepository(remoteDataSource)

    @Test
    fun `observeFavorites maps dto keys to domain id sets`() = runTest {
        val dto = UserFavoritesDto(
            categories = mapOf("android" to Timestamp(Date(1_000L))),
            subcategories = mapOf("android-testing" to Timestamp(Date(2_000L)), "kotlin-coroutines" to Timestamp(Date(3_000L))),
        )
        every { remoteDataSource.observeFavorites() } returns flowOf(dto)

        createRepository().observeFavorites().test {
            val favorites = awaitItem()
            favorites.categoryIds shouldBe mapOf("android" to Instant.ofEpochMilli(1_000L))
            favorites.subcategoryIds shouldBe mapOf(
                "android-testing" to Instant.ofEpochMilli(2_000L),
                "kotlin-coroutines" to Instant.ofEpochMilli(3_000L),
            )
            awaitComplete()
        }
    }

    @Test
    fun `observeFavorites emits empty sets when no favorites doc exists yet`() = runTest {
        every { remoteDataSource.observeFavorites() } returns flowOf(UserFavoritesDto())

        createRepository().observeFavorites().test {
            awaitItem() shouldBe UserFavorites.EMPTY
            awaitComplete()
        }
    }

    @Test
    fun `observeFavorites completes silently on permission denied without retrying`() = runTest {
        val attempts = AtomicInteger(0)
        every { remoteDataSource.observeFavorites() } returns flow {
            attempts.incrementAndGet()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeFavorites().test {
            awaitComplete()
        }
        attempts.get() shouldBe 1
    }

    @Test
    fun `observeFavorites retries and recovers after a non-permission listener failure`() = runTest {
        val attempts = AtomicInteger(0)
        every { remoteDataSource.observeFavorites() } returns flow {
            if (attempts.getAndIncrement() == 0) {
                throw FirebaseFirestoreException("listener dropped", FirebaseFirestoreException.Code.UNAVAILABLE)
            } else {
                emit(UserFavoritesDto(categories = mapOf("android" to Timestamp(Date(1_000L)))))
            }
        }

        createRepository().observeFavorites().test {
            val favorites = awaitItem()
            favorites.categoryIds shouldBe mapOf("android" to Instant.ofEpochMilli(1_000L))
            awaitComplete()
        }
        attempts.get() shouldBe 2
    }

    @Test
    fun `setCategoryFavorite delegates to data source and succeeds`() = runTest {
        coEvery { remoteDataSource.setCategoryFavorite("android", true) } just Runs

        val result = createRepository().setCategoryFavorite("android", true)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.setCategoryFavorite("android", true) }
    }

    @Test
    fun `setCategoryFavorite wraps data source failure in failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.setCategoryFavorite("android", false) } throws error

        val result = createRepository().setCategoryFavorite("android", false)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
    }

    @Test
    fun `setCategoryFavorite rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.setCategoryFavorite("android", true) } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().setCategoryFavorite("android", true) }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
    }

    @Test
    fun `setSubcategoryFavorite delegates to data source and succeeds`() = runTest {
        coEvery { remoteDataSource.setSubcategoryFavorite("android-testing", true) } just Runs

        val result = createRepository().setSubcategoryFavorite("android-testing", true)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.setSubcategoryFavorite("android-testing", true) }
    }

    @Test
    fun `setSubcategoriesFavorite delegates the whole set in a single data source call`() = runTest {
        val ids = setOf("android-testing", "kotlin-coroutines")
        coEvery { remoteDataSource.setSubcategoriesFavorite(ids, true) } just Runs

        val result = createRepository().setSubcategoriesFavorite(ids, true)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.setSubcategoriesFavorite(ids, true) }
    }

    @Test
    fun `setSubcategoriesFavorite wraps data source failure in failure result`() = runTest {
        val ids = setOf("android-testing")
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.setSubcategoriesFavorite(ids, false) } throws error

        val result = createRepository().setSubcategoriesFavorite(ids, false)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
    }
}
