package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/**
 * Pins a Category as a launcher shortcut. [params] is the Category's id — resolved here to a full
 * [com.rossomak.flashcards.core.domain.model.Category] so the [ShortcutTarget] gets a real
 * name/icon/color rather than stale values the caller happened to have on hand.
 *
 * `false` covers both an unresolvable [params] (deleted server-side between page load and tap) and
 * a launcher that doesn't support pinning — either way there is nothing to show the OS placement
 * dialog for, so the caller treats both the same.
 */
class PinCategoryShortcutUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val appShortcutsRepository: AppShortcutsRepository,
) : UseCase<String, Boolean> {

    override suspend operator fun invoke(params: String): Boolean {
        val category = flashcardRepository.fetchCategoriesByIds(setOf(params))
            .getOrNull()
            ?.firstOrNull()
            ?: return false
        return appShortcutsRepository.pinShortcut(
            ShortcutTarget(
                id = "category:${category.id}",
                name = category.name,
                route = "/study/category/${category.id}",
                iconSvg = category.iconSvg,
                color = category.color,
            ),
        )
    }
}
