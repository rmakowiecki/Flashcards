package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CategorySubcategoriesResolution
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryAvailability

class FakeFlashcardRepository : FlashcardRepository {
    var categoriesToReturn: Result<List<Category>> = Result.success(emptyList())
    var subcategoriesToReturn: Result<List<Subcategory>> = Result.success(emptyList())
    var categoriesByIdsToReturn: Result<List<Category>> = Result.success(emptyList())
    var subcategoriesByIdsToReturn: Result<List<Subcategory>> = Result.success(emptyList())
    var flashcardsToReturn: Result<List<Flashcard>> = Result.success(emptyList())
    val flashcardsBySubcategory: MutableMap<String, Result<List<Flashcard>>> = mutableMapOf()

    /** Prefix -> result, so a test can prove a repeated query was served from cache and never re-issued. */
    val searchResultsByPrefix: MutableMap<String, Result<List<Subcategory>>> = mutableMapOf()
    var searchResultsToReturn: Result<List<Subcategory>> = Result.success(emptyList())

    /** Every prefix [searchSubcategories] was called with, in call order. */
    val searchedPrefixes: MutableList<String> = mutableListOf()

    /** Every category id [fetchSubcategories] was called with, in call order. */
    val fetchedSubcategoryCategoryIds: MutableList<String> = mutableListOf()

    /** Every subcategory id [fetchFlashcards] was called with, in call order. */
    val fetchedSubcategoryIds: MutableList<String> = mutableListOf()

    /** How many times [invalidateFlashcardCache] was called, for tests that drive the cache seam. */
    var invalidationCount: Int = 0
        private set

    var cacheSeedToReturn: Result<Int> = Result.success(0)

    /** Availability per id for [resolveSubcategoryAvailability]; null means every requested id is Present. An id it lacks is Present too. */
    var subcategoryAvailabilityToReturn: Map<String, SubcategoryAvailability>? = null

    /** Every id set [resolveSubcategoryAvailability] was called with, in call order. */
    val resolvedAvailabilitySubcategoryIds: MutableList<Set<String>> = mutableListOf()

    /** What [resolveCategorySubcategories] returns; null derives it from [subcategoriesToReturn]. */
    var categorySubcategoriesResolutionToReturn: CategorySubcategoriesResolution? = null

    override suspend fun fetchCategories(): Result<List<Category>> = categoriesToReturn

    override suspend fun fetchSubcategories(categoryId: String): Result<List<Subcategory>> {
        fetchedSubcategoryCategoryIds += categoryId
        return subcategoriesToReturn
    }

    override suspend fun fetchCategoriesByIds(ids: Set<String>): Result<List<Category>> = categoriesByIdsToReturn

    override suspend fun fetchSubcategoriesByIds(ids: Set<String>): Result<List<Subcategory>> = subcategoriesByIdsToReturn

    override suspend fun resolveSubcategoryAvailability(ids: Set<String>): Map<String, SubcategoryAvailability> {
        resolvedAvailabilitySubcategoryIds += ids
        return ids.associateWith { id -> subcategoryAvailabilityToReturn?.get(id) ?: SubcategoryAvailability.Present }
    }

    /** Recorded in [fetchedSubcategoryCategoryIds], like [fetchSubcategories], so either read counts as the Category's fetch. */
    override suspend fun resolveCategorySubcategories(categoryId: String): CategorySubcategoriesResolution {
        fetchedSubcategoryCategoryIds += categoryId
        return categorySubcategoriesResolutionToReturn ?: subcategoriesToReturn.fold(
            onSuccess = { subcategories ->
                if (subcategories.isEmpty()) CategorySubcategoriesResolution.Missing else CategorySubcategoriesResolution.Present(subcategories)
            },
            onFailure = { CategorySubcategoriesResolution.Unknown },
        )
    }

    override suspend fun searchSubcategories(namePrefix: String): Result<List<Subcategory>> {
        searchedPrefixes += namePrefix
        return searchResultsByPrefix[namePrefix] ?: searchResultsToReturn
    }

    override suspend fun fetchFlashcards(subcategoryId: String): Result<List<Flashcard>> {
        fetchedSubcategoryIds += subcategoryId
        return flashcardsBySubcategory[subcategoryId] ?: flashcardsToReturn
    }

    override fun invalidateFlashcardCache() {
        invalidationCount++
    }

    override suspend fun fetchCacheSeed(): Result<Int> = cacheSeedToReturn
}
