package com.rossomak.flashcards.core.domain.model

/**
 * A Category's Subcategories, or why there are none.
 *
 * A Category always contains at least one Subcategory, so an empty answer from the server means the
 * Category itself is gone, while an empty answer served only from the on-device cache proves nothing.
 */
sealed interface CategorySubcategoriesResolution {

    /** The Category's Subcategories, never empty. */
    data class Present(val subcategories: List<Subcategory>) : CategorySubcategoriesResolution

    /** The server answered with no Subcategories: the Category no longer exists. */
    data object Missing : CategorySubcategoriesResolution

    /** The cache had nothing, or the read failed. */
    data object Unknown : CategorySubcategoriesResolution
}
