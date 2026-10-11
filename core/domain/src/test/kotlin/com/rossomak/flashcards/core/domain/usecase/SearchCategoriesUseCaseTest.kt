package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryWithParentCategory
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchCategoriesUseCaseTest {

    private val repository = FakeFlashcardRepository()
    private val searchCategories = SearchCategoriesUseCase(repository)

    private val android = category(
        id = "android",
        name = "Android",
        order = 0,
        featuredSubcategoryNames = listOf("Compose", "Coroutines", "Compose Navigation", "Testing", "Background"),
    )
    private val python = category(
        id = "python",
        name = "Python",
        order = 1,
        featuredSubcategoryNames = listOf("Async", "Typing", "Testing"),
    )
    private val allCategories = listOf(android, python)

    private val compose = subcategory(id = "android-compose", name = "Compose", parent = android, order = 0)
    private val composeNavigation =
        subcategory(id = "android-compose-navigation", name = "Compose Navigation", parent = android, order = 2)
    private val androidTesting = subcategory(id = "android-testing", name = "Testing", parent = android, order = 3)
    private val pythonTesting = subcategory(id = "python-testing", name = "Testing", parent = python, order = 2)

    @Test
    fun `query shorter than the minimum length returns empty results without querying`() = runTest {
        val results = search(query = "c").getOrThrow()

        results.isEmpty shouldBe true
        repository.searchedPrefixes shouldContainExactly emptyList()
    }

    @Test
    fun `query is trimmed and lowercased before it reaches the repository`() = runTest {
        search(query = "  CoMpOsE  ")

        repository.searchedPrefixes shouldContainExactly listOf("compose")
    }

    @Test
    fun `matched subcategories are returned in the order the repository produced them`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose, composeNavigation))

        val results = search(query = "compose").getOrThrow()

        results.subcategories shouldContainExactly listOf(
            SubcategoryWithParentCategory(compose, android),
            SubcategoryWithParentCategory(composeNavigation, android),
        )
    }

    @Test
    fun `a category surfaces when a child subcategory matched even though its own name did not`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

        val results = search(query = "compose").getOrThrow()

        results.categories.map { it.category } shouldContainExactly listOf(android)
    }

    @Test
    fun `a category surfaces on its own name prefix when no subcategory matched`() = runTest {
        repository.searchResultsByPrefix["and"] = Result.success(emptyList())

        val results = search(query = "and").getOrThrow()

        results.subcategories shouldContainExactly emptyList()
        results.categories.map { it.category } shouldContainExactly listOf(android)
    }

    @Test
    fun `category name matching is case-insensitive`() = runTest {
        val results = search(query = "ANDR").getOrThrow()

        results.categories.map { it.category } shouldContainExactly listOf(android)
    }

    @Test
    fun `every parent of a matched subcategory surfaces, ordered by category order`() = runTest {
        repository.searchResultsByPrefix["testing"] = Result.success(listOf(androidTesting, pythonTesting))

        val results = search(query = "testing").getOrThrow()

        results.categories.map { it.category } shouldContainExactly listOf(android, python)
    }

    @Test
    fun `a parent id missing from the supplied categories drops its subcategory and category rather than faking them`() = runTest {
        repository.searchResultsByPrefix["testing"] = Result.success(listOf(androidTesting, pythonTesting))

        val results = searchCategories(
            SearchCategoriesParams(query = "testing", categories = listOf(android)),
        ).getOrThrow()

        results.subcategories.map { it.subcategory } shouldContainExactly listOf(androidTesting)
        results.categories.map { it.category } shouldContainExactly listOf(android)
    }

    @Test
    fun `chip line hoists the matched subcategory ahead of the stored prominence names`() = runTest {
        repository.searchResultsByPrefix["testing"] = Result.success(listOf(androidTesting))

        val results = search(query = "testing").getOrThrow()

        results.categories.single().subcategorySummary shouldContainExactly
            listOf("Testing", "Compose", "Coroutines", "Compose Navigation", "Background")
    }

    @Test
    fun `chip line orders multiple matches by prominence, not by the order they were returned`() = runTest {
        // Handed back in an order that contradicts prominence (Testing is order 3, Compose is 0),
        // so a chip line that merely preserved the repository's order would fail this.
        repository.searchResultsByPrefix["co"] = Result.success(listOf(androidTesting, composeNavigation, compose))

        val results = search(query = "co").getOrThrow()

        results.categories.single().subcategorySummary.take(3) shouldContainExactly
            listOf("Compose", "Compose Navigation", "Testing")
    }

    @Test
    fun `chip line drops backfill names already hoisted and stops at five`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose, composeNavigation))

        val results = search(query = "compose").getOrThrow()

        results.categories.single().subcategorySummary shouldContainExactly
            listOf("Compose", "Compose Navigation", "Coroutines", "Testing", "Background")
    }

    @Test
    fun `chip line falls back to stored prominence names when nothing in that category matched`() = runTest {
        repository.searchResultsByPrefix["python"] = Result.success(emptyList())

        val results = search(query = "python").getOrThrow()

        results.categories.single().subcategorySummary shouldContainExactly listOf("Async", "Typing", "Testing")
    }

    @Test
    fun `repeating a query serves the cached result instead of querying again`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

        search(query = "compose")
        val second = search(query = "compose").getOrThrow()

        repository.searchedPrefixes shouldContainExactly listOf("compose")
        second.subcategories.map { it.subcategory } shouldContainExactly listOf(compose)
    }

    @Test
    fun `a shorter prefix of a cached query is still queried live`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
        repository.searchResultsByPrefix["compos"] = Result.success(listOf(compose, composeNavigation))

        search(query = "compose")
        val refined = search(query = "compos").getOrThrow()

        repository.searchedPrefixes shouldContainExactly listOf("compose", "compos")
        refined.subcategories.map { it.subcategory } shouldContainExactly listOf(compose, composeNavigation)
    }

    @Test
    fun `a failed query propagates and is not cached`() = runTest {
        repository.searchResultsByPrefix["compose"] = Result.failure(IllegalStateException("offline"))

        search(query = "compose").isFailure shouldBe true

        repository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
        search(query = "compose").getOrThrow().subcategories.map { it.subcategory } shouldContainExactly listOf(compose)
        repository.searchedPrefixes shouldContainExactly listOf("compose", "compose")
    }

    private suspend fun search(query: String) =
        searchCategories(SearchCategoriesParams(query = query, categories = allCategories))

    private fun category(
        id: String,
        name: String,
        order: Int,
        featuredSubcategoryNames: List<String>,
    ) = Category(
        id = id,
        name = name,
        order = order,
        subcategoryCount = featuredSubcategoryNames.size,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = featuredSubcategoryNames,
    )

    private fun subcategory(id: String, name: String, parent: Category, order: Int) = Subcategory(
        id = id,
        name = name,
        categoryId = parent.id,
        categoryName = parent.name,
        order = order,
        cardCount = 1,
    )
}
