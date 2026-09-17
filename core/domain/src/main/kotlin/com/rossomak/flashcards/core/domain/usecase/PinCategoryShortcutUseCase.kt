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
 * Pins a Category as a launcher shortcut. [params] is the Category's id — resolved here to a full
 * [com.rossomak.flashcards.core.domain.model.Category] so the [ShortcutTarget] gets a real
 * name/icon/color rather than stale values the caller happened to have on hand.
 */
class PinCategoryShortcutUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val appShortcutsRepository: AppShortcutsRepository,
) : UseCase<String, PinShortcutResult> {

    override suspend operator fun invoke(params: String): PinShortcutResult {
        val category = flashcardRepository.fetchCategoriesByIds(setOf(params))
            .getOrNull()
            ?.firstOrNull()
            ?: return EntityResolutionError
        val pinningResult = appShortcutsRepository.pinShortcut(
            ShortcutTarget(
                id = "category:${category.id}",
                name = category.name,
                route = ShortcutRoute.category(category.id),
                iconSvg = category.iconSvg,
                color = category.color,
            ),
        )
        return if (pinningResult) Pinned else UnsupportedLauncher
    }
}
