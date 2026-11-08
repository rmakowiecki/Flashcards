package com.rossomak.flashcards.presentation.shortcut

import com.rossomak.flashcards.core.domain.model.ShortcutRoute
import com.rossomak.flashcards.core.domain.usecase.GetCategoryUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryUseCase
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination.ToCategoryDetails
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination.ToSubcategoryDetails
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns a launcher shortcut's route into the Details screen it opens. Null means the route did not
 * parse, its target no longer resolves, a Subcategory now sits under a different Category than the
 * route names, or the lookup did not answer within [RESOLVE_TIMEOUT_MS].
 */
class ShortcutRouteResolver @Inject constructor(
    private val getCategory: GetCategoryUseCase,
    private val getSubcategory: GetSubcategoryUseCase,
) {

    suspend fun resolve(route: String): ShortcutDestination? = withTimeoutOrNull(RESOLVE_TIMEOUT_MS.milliseconds) {
        val subcategoryMatch = ShortcutRoute.SUBCATEGORY_ROUTE_REGEX.matchEntire(route)
        val categoryMatch = ShortcutRoute.CATEGORY_ROUTE_REGEX.matchEntire(route)
        when {
            subcategoryMatch != null -> resolveSubcategory(
                categoryId = subcategoryMatch.groupValues[1],
                subcategoryId = subcategoryMatch.groupValues[2],
            )
            categoryMatch != null -> resolveCategory(categoryMatch.groupValues[1])
            else -> null
        }
    }

    private suspend fun resolveSubcategory(categoryId: String, subcategoryId: String): ShortcutDestination? =
        getSubcategory(subcategoryId).getOrNull()?.takeIf { it.categoryId == categoryId }?.run {
            ToSubcategoryDetails(
                SubcategoryDetailsRoute(categoryId = categoryId, categoryName = categoryName, subcategoryId = id, subcategoryName = name),
            )
        }

    private suspend fun resolveCategory(categoryId: String): ShortcutDestination? = getCategory(categoryId).getOrNull()?.run {
        ToCategoryDetails(CategoryDetailsRoute(categoryId = id, categoryName = name))
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 1000L
    }
}
