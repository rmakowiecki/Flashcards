package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/**
 * Pins a Subcategory as a launcher shortcut. [params] is the Subcategory's id — resolved here to
 * the Subcategory itself, then to its parent Category for [ShortcutTarget.iconSvg]/[ShortcutTarget.color]:
 * a Subcategory carries no icon/color of its own (same rule [com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory] documents).
 *
 * `false` covers an unresolvable [params]/parent Category and a launcher that doesn't support
 * pinning alike — either way there is nothing to show the OS placement dialog for.
 */
class PinSubcategoryShortcutUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val appShortcutsRepository: AppShortcutsRepository,
) : UseCase<String, Boolean> {

    override suspend operator fun invoke(params: String): Boolean {
        val subcategory = flashcardRepository.fetchSubcategoriesByIds(setOf(params))
            .getOrNull()
            ?.firstOrNull()
            ?: return false
        val category = flashcardRepository.fetchCategoriesByIds(setOf(subcategory.categoryId))
            .getOrNull()
            ?.firstOrNull()
            ?: return false
        return appShortcutsRepository.pinShortcut(
            ShortcutTarget(
                id = "subcategory:${subcategory.id}",
                name = subcategory.name,
                route = "/study/category/${subcategory.categoryId}/subcategory/${subcategory.id}",
                iconSvg = category.iconSvg,
                color = category.color,
            ),
        )
    }
}
