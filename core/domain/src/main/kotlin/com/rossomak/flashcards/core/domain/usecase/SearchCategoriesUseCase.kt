package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CategorySearchResults
import com.rossomak.flashcards.core.domain.model.CategoryWithSubcategorySummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryWithParentCategory
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [categories] is the already-loaded category list the screen holds — searching Category names is
 * a local operation over that list and costs no read, which is why the use case takes it as input
 * rather than fetching it. Only Subcategories are queried live.
 */
data class SearchCategoriesParams(
    val query: String,
    val categories: List<Category>,
)

/**
 * Runs one category/subcategory search: the live prefix query for matching Subcategories, the
 * local prefix match over already-loaded Categories, and the chip-line construction that pairs
 * the two.
 *
 * Holds an in-memory cache of Subcategory results keyed on the exact normalized query, so the
 * same query is never issued twice. It is deliberately not prefix-aware — `"testin"` after
 * `"testing"` is a fresh query, not a local filter of the cached superset — and deliberately not
 * persisted: the cache lives and dies with this instance, which (being unscoped) means it lives
 * as long as the ViewModel that injected it.
 *
 * See docs/design/category-search.md.
 */
class SearchCategoriesUseCase @Inject constructor(
    private val repository: FlashcardRepository
) : UseCase<SearchCategoriesParams, Result<CategorySearchResults>> {

    private val cacheMutex = Mutex()
    private val cachedSubcategoriesByQuery = mutableMapOf<String, List<Subcategory>>()

    override suspend operator fun invoke(params: SearchCategoriesParams): Result<CategorySearchResults> {
        val normalizedQuery = params.query.trim().lowercase()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) return Result.success(CategorySearchResults.EMPTY)

        val cachedSubcategories = cacheMutex.withLock { cachedSubcategoriesByQuery[normalizedQuery] }
        if (cachedSubcategories != null) {
            return Result.success(buildResults(normalizedQuery, cachedSubcategories, params.categories))
        }

        return repository.searchSubcategories(normalizedQuery)
            .onSuccess { subcategories ->
                cacheMutex.withLock { cachedSubcategoriesByQuery[normalizedQuery] = subcategories }
            }
            .map { subcategories -> buildResults(normalizedQuery, subcategories, params.categories) }
    }

    /**
     * A Category earns a place in the results either by parenting a matched Subcategory (resolved
     * from the match's denormalized `categoryId`, no extra read) or by prefix-matching on its own
     * name. Both branches are load-bearing: the first is what surfaces "Android" for a search of
     * "compose", the second is what surfaces it for a search of "and" when no Subcategory matches
     * at all. A parent id with no entry in [categories] — a stale or partial category list — is
     * dropped rather than faked into a row, in both sections.
     */
    private fun buildResults(
        normalizedQuery: String,
        subcategories: List<Subcategory>,
        categories: List<Category>,
    ): CategorySearchResults {
        val subcategoriesByCategoryId = subcategories.groupBy { it.categoryId }
        val nameMatchedCategoryIds = categories
            .filter { it.name.lowercase().startsWith(normalizedQuery) }
            .map { it.id }
        val matchedCategoryIds = subcategoriesByCategoryId.keys + nameMatchedCategoryIds

        val matchedCategories = categories
            .filter { it.id in matchedCategoryIds }
            .sortedBy { it.order }
            .map { category ->
                CategoryWithSubcategorySummary(
                    category = category,
                    subcategorySummary = subcategorySummary(category, subcategoriesByCategoryId[category.id].orEmpty()),
                )
            }

        val categoriesById = categories.associateBy(Category::id)
        val subcategoriesWithParent = subcategories.mapNotNull { subcategory ->
            val parentCategory = categoriesById[subcategory.categoryId] ?: return@mapNotNull null
            SubcategoryWithParentCategory(subcategory = subcategory, parentCategory = parentCategory)
        }
        return CategorySearchResults(subcategories = subcategoriesWithParent, categories = matchedCategories)
    }

    /**
     * Matched Subcategories lead the chip line, ranked among themselves by prominence
     * [Subcategory.order] — not by the name ordering the Topics section uses, which is a property
     * of that flat list rather than of a chip line. The remainder backfills from the Category's
     * stored prominence names, skipping anything already hoisted; `distinct()` keeps first
     * occurrences, so the hoisted matches survive and their duplicates in `featuredSubcategoryNames`
     * drop out.
     */
    private fun subcategorySummary(category: Category, matchedSubcategories: List<Subcategory>): List<String> =
        (matchedSubcategories.sortedBy { it.order }.map { it.name } + category.featuredSubcategoryNames)
            .distinct()
            .take(SUBCATEGORY_SUMMARY_LIMIT)

    companion object {
        /**
         * Below this, the screen stays in its default state and no query is issued at all. A
         * single character matches a large and growing slice of the collection — effectively the
         * bulk load this design exists to avoid.
         */
        const val MIN_QUERY_LENGTH = 2

        /** Names a chip line carries at most; the UI truncates further by available width. */
        const val SUBCATEGORY_SUMMARY_LIMIT = 5
    }
}
