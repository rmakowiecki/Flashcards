package com.rossomak.flashcards.core.domain.model

/**
 * Single source of truth for the ADR-0003 shortcut route shape (`/study/category/{id}` or
 * `/study/category/{id}/subcategory/{id}`) — [PinCategoryShortcutUseCase]/[PinSubcategoryShortcutUseCase]
 * build a [ShortcutTarget.route] with these, `SplashViewModel` parses one back with these, and
 * both sides move together if the shape ever changes.
 */
object ShortcutRoute {

    val CATEGORY_ROUTE_REGEX = Regex("^/study/category/([^/]+)$")
    val SUBCATEGORY_ROUTE_REGEX = Regex("^/study/category/([^/]+)/subcategory/([^/]+)$")

    fun category(categoryId: String): String = "/study/category/$categoryId"

    fun subcategory(categoryId: String, subcategoryId: String): String =
        "/study/category/$categoryId/subcategory/$subcategoryId"
}
