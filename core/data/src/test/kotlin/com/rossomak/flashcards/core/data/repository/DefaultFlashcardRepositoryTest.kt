package com.rossomak.flashcards.core.data.repository

import android.util.Log
import com.rossomak.flashcards.core.data.model.CategoryDto
import com.rossomak.flashcards.core.data.model.FlashcardDto
import com.rossomak.flashcards.core.data.model.SubcategoryDto
import com.rossomak.flashcards.core.data.source.FlashcardReadSource
import com.rossomak.flashcards.core.data.source.FlashcardRemoteDataSource
import com.rossomak.flashcards.core.data.source.SubcategoryQueryPage
import com.rossomak.flashcards.core.domain.model.CategorySubcategoriesResolution
import com.rossomak.flashcards.core.domain.model.SubcategoryAvailability.Missing
import com.rossomak.flashcards.core.domain.model.SubcategoryAvailability.Present
import com.rossomak.flashcards.core.domain.model.SubcategoryAvailability.Unknown
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val CATEGORY_ID = "cat-1"
private const val PRESENT_ID = "sub-present"
private const val ABSENT_ID = "sub-absent"

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFlashcardRepositoryTest {

    private val remoteDataSource: FlashcardRemoteDataSource = mockk()

    private fun createRepository(): DefaultFlashcardRepository =
        DefaultFlashcardRepository(remoteDataSource)

    @Before
    fun setUp() {
        // invalidateFlashcardCache() logs via android.util.Log, unavailable outside instrumented/
        // Robolectric tests — stub it rather than pull in either just for this one call.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `fetchCategories maps dtos to domain in order`() = runTest {
        val categoryDto = CategoryDto(id = "cat-1", name = "Android", order = 2, subcategoryCount = 3, iconSvg = "<svg />", color = "#6B2FA0")
        coEvery { remoteDataSource.getCategories() } returns listOf(categoryDto)

        val result = createRepository().fetchCategories()

        result.isSuccess shouldBe true
        val category = result.getOrThrow().single()
        category.id shouldBe categoryDto.id
        category.name shouldBe categoryDto.name
        category.order shouldBe categoryDto.order
        category.subcategoryCount shouldBe categoryDto.subcategoryCount
        category.iconSvg shouldBe categoryDto.iconSvg
        category.color shouldBe categoryDto.color
        coVerify(exactly = 1) { remoteDataSource.getCategories() }
    }

    @Test
    fun `fetchCategories wraps data source failure in failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getCategories() } throws error

        val result = createRepository().fetchCategories()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getCategories() }
    }

    @Test
    fun `fetchCategories rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.getCategories() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().fetchCategories() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getCategories() }
    }

    @Test
    fun `fetchSubcategories forwards category id and maps dtos to domain`() = runTest {
        val categoryId = "cat-1"
        val subcategoryDto = SubcategoryDto(
            id = "sub-1",
            name = "Compose",
            categoryId = categoryId,
            categoryName = "Android",
            order = 1,
            cardCount = 12,
        )
        coEvery { remoteDataSource.getSubcategoriesByCategoryId(categoryId) } returns listOf(subcategoryDto)

        val result = createRepository().fetchSubcategories(categoryId)

        result.isSuccess shouldBe true
        val subcategory = result.getOrThrow().single()
        subcategory.id shouldBe subcategoryDto.id
        subcategory.categoryId shouldBe categoryId
        subcategory.cardCount shouldBe subcategoryDto.cardCount
        coVerify(exactly = 1) { remoteDataSource.getSubcategoriesByCategoryId(categoryId) }
    }

    @Test
    fun `fetchSubcategories wraps data source failure in failure result`() = runTest {
        val categoryId = "cat-1"
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getSubcategoriesByCategoryId(categoryId) } throws error

        val result = createRepository().fetchSubcategories(categoryId)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getSubcategoriesByCategoryId(categoryId) }
    }

    @Test
    fun `resolveSubcategoryAvailability marks ids absent from a server answer as Missing`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByIds(listOf(PRESENT_ID, ABSENT_ID)) } returns
            SubcategoryQueryPage(subcategories = listOf(subcategoryDto(PRESENT_ID)), isFromCache = false)

        val availability = createRepository().resolveSubcategoryAvailability(setOf(PRESENT_ID, ABSENT_ID))

        availability shouldBe mapOf(PRESENT_ID to Present, ABSENT_ID to Missing)
    }

    @Test
    fun `resolveSubcategoryAvailability marks ids absent from a cache-only answer as Unknown`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByIds(listOf(PRESENT_ID, ABSENT_ID)) } returns
            SubcategoryQueryPage(subcategories = listOf(subcategoryDto(PRESENT_ID)), isFromCache = true)

        val availability = createRepository().resolveSubcategoryAvailability(setOf(PRESENT_ID, ABSENT_ID))

        availability shouldBe mapOf(PRESENT_ID to Present, ABSENT_ID to Unknown)
    }

    @Test
    fun `resolveSubcategoryAvailability marks only the ids of a failed batch as Unknown`() = runTest {
        val ids = (1..FlashcardRemoteDataSource.WHEREIN_BATCH_SIZE + 1).map { index -> "sub-$index" }
        val (firstBatch, secondBatch) = ids.chunked(FlashcardRemoteDataSource.WHEREIN_BATCH_SIZE)
        coEvery { remoteDataSource.getSubcategoryPageByIds(firstBatch) } throws IllegalStateException("firestore down")
        coEvery { remoteDataSource.getSubcategoryPageByIds(secondBatch) } returns
            SubcategoryQueryPage(subcategories = emptyList(), isFromCache = false)

        val availability = createRepository().resolveSubcategoryAvailability(ids.toSet())

        availability shouldBe firstBatch.associateWith { Unknown } + secondBatch.associateWith { Missing }
    }

    @Test
    fun `resolveSubcategoryAvailability rethrows cancellation instead of marking ids Unknown`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByIds(listOf(PRESENT_ID)) } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().resolveSubcategoryAvailability(setOf(PRESENT_ID)) }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
    }

    @Test
    fun `resolveCategorySubcategories maps a non-empty answer to Present`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByCategoryId(CATEGORY_ID) } returns
            SubcategoryQueryPage(subcategories = listOf(subcategoryDto(PRESENT_ID)), isFromCache = true)

        val resolution = createRepository().resolveCategorySubcategories(CATEGORY_ID)

        (resolution as CategorySubcategoriesResolution.Present).subcategories.map { it.id } shouldBe listOf(PRESENT_ID)
    }

    @Test
    fun `resolveCategorySubcategories maps an empty server answer to Missing`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByCategoryId(CATEGORY_ID) } returns
            SubcategoryQueryPage(subcategories = emptyList(), isFromCache = false)

        createRepository().resolveCategorySubcategories(CATEGORY_ID) shouldBe CategorySubcategoriesResolution.Missing
    }

    @Test
    fun `resolveCategorySubcategories maps an empty cache-only answer to Unknown`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByCategoryId(CATEGORY_ID) } returns
            SubcategoryQueryPage(subcategories = emptyList(), isFromCache = true)

        createRepository().resolveCategorySubcategories(CATEGORY_ID) shouldBe CategorySubcategoriesResolution.Unknown
    }

    @Test
    fun `resolveCategorySubcategories maps a failed read to Unknown`() = runTest {
        coEvery { remoteDataSource.getSubcategoryPageByCategoryId(CATEGORY_ID) } throws IllegalStateException("firestore down")

        createRepository().resolveCategorySubcategories(CATEGORY_ID) shouldBe CategorySubcategoriesResolution.Unknown
    }

    private fun subcategoryDto(id: String) = SubcategoryDto(id = id, name = "Compose", categoryId = CATEGORY_ID, categoryName = "Android", order = 1, cardCount = 12)

    @Test
    fun `fetchFlashcards drops cards with null difficulty and forwards subcategory id`() = runTest {
        val subcategoryId = "sub-1"
        val ratedDto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        val unratedDto = FlashcardDto(id = "card-2", question = "q2", answer = "a2", difficulty = null)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } returns listOf(ratedDto, unratedDto)

        val result = createRepository().fetchFlashcards(subcategoryId)

        result.isSuccess shouldBe true
        val flashcard = result.getOrThrow().single()
        flashcard.id shouldBe ratedDto.id
        flashcard.subcategoryId shouldBe subcategoryId
        flashcard.difficulty shouldBe 4
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
    }

    @Test
    fun `fetchFlashcards wraps data source failure in failure result`() = runTest {
        val subcategoryId = "sub-1"
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws error
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns emptyList()

        val result = createRepository().fetchFlashcards(subcategoryId)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
    }

    @Test
    fun `fetchFlashcards serves cached cards when the server read fails`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws IllegalStateException("offline")
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns listOf(dto)

        val result = createRepository().fetchFlashcards(subcategoryId)

        result.getOrThrow().single().id shouldBe dto.id
    }

    @Test
    fun `fetchFlashcards tries the server again after serving cached cards for a failed server read`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws IllegalStateException("offline")
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns listOf(dto)
        val repository = createRepository()

        repository.fetchFlashcards(subcategoryId)
        repository.fetchFlashcards(subcategoryId)

        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
    }

    @Test
    fun `fetchFlashcards reports the server failure when the cache read after it also fails`() = runTest {
        val subcategoryId = "sub-1"
        val error = IllegalStateException("offline")
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws error
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } throws IllegalStateException("no cache")

        val result = createRepository().fetchFlashcards(subcategoryId)

        result.exceptionOrNull() shouldBe error
    }

    @Test
    fun `searchSubcategories forwards prefix and maps dtos to domain`() = runTest {
        val namePrefix = "compo"
        val subcategoryDto = SubcategoryDto(
            id = "sub-1",
            name = "Compose",
            categoryId = "cat-1",
            categoryName = "Android",
            order = 1,
            cardCount = 12,
        )
        coEvery { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) } returns listOf(subcategoryDto)

        val result = createRepository().searchSubcategories(namePrefix)

        result.isSuccess shouldBe true
        val subcategory = result.getOrThrow().single()
        subcategory.id shouldBe subcategoryDto.id
        subcategory.name shouldBe subcategoryDto.name
        subcategory.categoryId shouldBe subcategoryDto.categoryId
        coVerify(exactly = 1) { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) }
    }

    @Test
    fun `searchSubcategories wraps data source failure in failure result`() = runTest {
        val namePrefix = "compo"
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) } throws error

        val result = createRepository().searchSubcategories(namePrefix)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) }
    }

    @Test
    fun `searchSubcategories rethrows cancellation instead of wrapping it`() = runTest {
        val namePrefix = "compo"
        coEvery { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().searchSubcategories(namePrefix) }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix) }
    }

    @Test
    fun `fetchFlashcards serves a repeat read from cache without contacting the server`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } returns listOf(dto)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns listOf(dto)
        val repository = createRepository()

        repository.fetchFlashcards(subcategoryId)
        val second = repository.fetchFlashcards(subcategoryId)
        val third = repository.fetchFlashcards(subcategoryId)

        second.getOrThrow().single().id shouldBe dto.id
        third.getOrThrow().single().id shouldBe dto.id
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) }
    }

    @Test
    fun `fetchFlashcards falls through to the server when the cache read comes back empty`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } returns listOf(dto)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns emptyList()
        val repository = createRepository()

        repository.fetchFlashcards(subcategoryId)
        val afterEviction = repository.fetchFlashcards(subcategoryId)

        afterEviction.getOrThrow().single().id shouldBe dto.id
        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) }
    }

    @Test
    fun `fetchFlashcards reads from the server again for the first read of a new generation`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } returns listOf(dto)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns listOf(dto)
        val repository = createRepository()

        repository.fetchFlashcards(subcategoryId)
        repository.fetchFlashcards(subcategoryId)
        repository.invalidateFlashcardCache()
        repository.fetchFlashcards(subcategoryId)
        repository.fetchFlashcards(subcategoryId)

        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) }
    }

    @Test
    fun `fetchFlashcards caches each subcategory independently`() = runTest {
        val first = "sub-1"
        val second = "sub-2"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(any(), FlashcardReadSource.Server) } returns listOf(dto)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(any(), FlashcardReadSource.Cache) } returns listOf(dto)
        val repository = createRepository()

        repository.fetchFlashcards(first)
        repository.fetchFlashcards(second)
        repository.fetchFlashcards(first)

        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(first, FlashcardReadSource.Server) }
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(second, FlashcardReadSource.Server) }
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(first, FlashcardReadSource.Cache) }
        coVerify(exactly = 0) { remoteDataSource.getFlashcardsBySubcategoryId(second, FlashcardReadSource.Cache) }
    }

    @Test
    fun `fetchFlashcards retries the server after a failed read with nothing cached`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws
            IllegalStateException("firestore down") andThen listOf(dto)
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) } returns emptyList()
        val repository = createRepository()

        repository.fetchFlashcards(subcategoryId).isFailure shouldBe true
        val retried = repository.fetchFlashcards(subcategoryId)

        retried.getOrThrow().single().id shouldBe dto.id
        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
        coVerify(exactly = 1) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache) }
    }

    @Test
    fun `fetchFlashcards rethrows cancellation rather than wrapping it in a failure`() = runTest {
        val subcategoryId = "sub-1"
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } throws
            CancellationException("cancelled")
        val repository = createRepository()

        var thrown: Throwable? = null
        try {
            repository.fetchFlashcards(subcategoryId)
        } catch (exception: CancellationException) {
            thrown = exception
        }

        (thrown is CancellationException) shouldBe true
    }

    @Test
    fun `fetchCacheSeed returns the remote value`() = runTest {
        coEvery { remoteDataSource.getCacheSeed() } returns 7

        val result = createRepository().fetchCacheSeed()

        result.getOrThrow() shouldBe 7
        coVerify(exactly = 1) { remoteDataSource.getCacheSeed() }
    }

    @Test
    fun `fetchCacheSeed wraps data source failure in failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getCacheSeed() } throws error

        val result = createRepository().fetchCacheSeed()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
    }

    @Test
    fun `a read in flight when invalidation lands is not stamped into the new generation`() = runTest {
        val subcategoryId = "sub-1"
        val dto = FlashcardDto(id = "card-1", question = "q", answer = "a", difficulty = 4)
        val readStarted = CompletableDeferred<Unit>()
        val readGate = CompletableDeferred<Unit>()
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } coAnswers {
            readStarted.complete(Unit)
            readGate.await()
            listOf(dto)
        }
        val repository = createRepository()

        // Started under generation 0, but doesn't reach the server response until after invalidation.
        val inFlightRead = async { repository.fetchFlashcards(subcategoryId) }
        readStarted.await()
        repository.invalidateFlashcardCache()
        readGate.complete(Unit)
        inFlightRead.await().getOrThrow().single().id shouldBe dto.id

        // A second read must still hit the server: the in-flight read must not have been stamped
        // as belonging to generation 1, even though its response only arrived after the bump.
        coEvery { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) } returns listOf(dto)
        repository.fetchFlashcards(subcategoryId)

        coVerify(exactly = 2) { remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server) }
    }

    @Test
    fun `fetchCacheSeed rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.getCacheSeed() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().fetchCacheSeed() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
    }
}
