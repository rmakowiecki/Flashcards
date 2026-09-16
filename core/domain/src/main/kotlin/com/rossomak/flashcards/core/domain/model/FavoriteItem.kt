package com.rossomak.flashcards.core.domain.model

import java.time.Instant

/**
 * A favorited Category or Subcategory resolved to its full object, shared across every consumer
 * that renders a User's favorites as a display list — today the home screen's favorites carousel,
 * later the dynamic launcher shortcuts (see docs/design/launcher-shortcuts.md). Both variants
 * carry [favoritedAt] so consumers can sort categories and subcategories together,
 * most-recently-favorited first.
 */
sealed class FavoriteItem {
    abstract val favoritedAt: Instant

    data class FavoriteCategory(
        val category: Category,
        override val favoritedAt: Instant,
    ) : FavoriteItem()

    /**
     * @param parentCategory [Subcategory] carries no icon/color of its own — every consumer that
     * needs to render this item's glyph (the home carousel, later the launcher shortcut) reads it
     * from here instead.
     */
    data class FavoriteSubcategory(
        val subcategory: Subcategory,
        val parentCategory: Category,
        override val favoritedAt: Instant,
    ) : FavoriteItem()
}
