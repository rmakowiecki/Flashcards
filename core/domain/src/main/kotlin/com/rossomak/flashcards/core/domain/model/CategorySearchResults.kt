package com.rossomak.flashcards.core.domain.model

/**
 * One category/subcategory search, split into the two sections the Browse screen renders
 * (labelled "Topics" and "Categories" in the UI): [subcategories] above [categories].
 *
 * [subcategories] are the Subcategories whose own name matched, paired with their parent, in the
 * order the query returned them (name ascending). [categories] are every Category that either parents one of those
 * Subcategories or whose own name matched — a Category can therefore appear without any of its
 * Subcategories matching, and vice versa. See docs/design/category-search.md.
 */
data class CategorySearchResults(
    val subcategories: List<SubcategoryWithParentCategory>,
    val categories: List<CategoryWithSubcategorySummary>,
) {
    val isEmpty: Boolean get() = subcategories.isEmpty() && categories.isEmpty()

    companion object {
        val EMPTY = CategorySearchResults(subcategories = emptyList(), categories = emptyList())
    }
}

/**
 * A Category paired with the Subcategory names to render in its chip line. The summary is *not*
 * always `category.featuredSubcategoryNames`: in search results the Subcategories that matched the
 * query are hoisted to the front, and the rest of the line is backfilled from
 * `featuredSubcategoryNames`. Outside search the two are the same thing, which is why the default
 * Browse list uses this type too.
 */
data class CategoryWithSubcategorySummary(
    val category: Category,
    val subcategorySummary: List<String>,
)

/** A matched Subcategory with its parent Category, which carries the icon and color it renders with. */
data class SubcategoryWithParentCategory(
    val subcategory: Subcategory,
    val parentCategory: Category,
)
