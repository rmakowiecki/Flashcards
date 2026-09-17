package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.PinShortcutResult
import com.rossomak.flashcards.core.domain.model.PinShortcutResult.EntityResolutionError
import com.rossomak.flashcards.core.domain.model.PinShortcutResult.Pinned
import com.rossomak.flashcards.core.domain.model.PinShortcutResult.UnsupportedLauncher
import com.rossomak.flashcards.core.domain.model.ShortcutRoute
import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/**
 * Pins a Subcategory as a launcher shortcut. [params] is the Subcategory's id — resolved here to
 * the Subcategory itself, then to its parent Category for [ShortcutTarget.iconSvg]/[ShortcutTarget.color]:
 * a Subcategory carries no icon/color of its own (same rule [com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory] documents).
 */
class PinSubcategoryShortcutUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val appShortcutsRepository: AppShortcutsRepository,
) : UseCase<String, PinShortcutResult> {

    override suspend operator fun invoke(params: String): PinShortcutResult {
        val subcategory = flashcardRepository.fetchSubcategoriesByIds(setOf(params))
            .getOrNull()
            ?.firstOrNull()
            ?: return EntityResolutionError
        val category = flashcardRepository.fetchCategoriesByIds(setOf(subcategory.categoryId))
            .getOrNull()
            ?.firstOrNull()
            ?: return EntityResolutionError
        val pinningResult = appShortcutsRepository.pinShortcut(
            ShortcutTarget(
                id = "subcategory:${subcategory.id}",
                name = subcategory.name,
                route = ShortcutRoute.subcategory(subcategory.categoryId, subcategory.id),
                iconSvg = category.iconSvg,
                color = category.color,
            ),
        )
        return if (pinningResult) Pinned else UnsupportedLauncher
    }
}
